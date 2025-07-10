package com.example.powblockchain.service;

import com.example.powblockchain.*;
import com.example.powblockchain.Void;
import com.example.powblockchain.controller.NodeController;
import com.example.powblockchain.helperFunc.HashSHA256;
import com.example.powblockchain.model.Mempool;
import com.example.powblockchain.model.Node;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

@Service
public class TransactionService extends NodeGrpc.NodeImplBase{

    Node node;

    @Autowired
    NodeController nodeController;

    @Autowired
    public TransactionService(@Value("${grpc.port}") int port, Node node) {
        this.node = node;
        try {
             Server server = ServerBuilder.forPort(port)
                    .addService(this)
                    .build()
                    .start();
            node.setServer(server);
            System.out.println("Server started, listening on " + port);
//            wallet.connect(serverIp);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void handleTransaction(Transaction txWithIp, StreamObserver<Void> responseObserver) {
        responseObserver.onNext(Void.getDefaultInstance());
        responseObserver.onCompleted();
        String incomingIp = txWithIp.getIpAddr();
        Transaction txRequest = txWithIp.toBuilder().clearIpAddr().build();

        String txID = HashSHA256.hashObject(txRequest);

        Mempool mempool = node.getPool();

        //Make sure one transaction gets processed each time
        synchronized (this) {
        try{
            String fullIp = node.getServerIp()+":"+node.getRpcPort();
            if (!mempool.has(txID) && mempool.validateTx(txRequest,node.getChain().getUTXO(),incomingIp, fullIp)) {
    //            System.out.println("tx sent to: " + node.getVersion().getIpAddr() + " Hash: " + txID);
                nodeController.sendTransactions();
                mempool.addTx(txRequest);

                //If orphan pool contains child transaction, validate child again
                if(mempool.getOrphanPool().containsKey(txID)){
                    node.sendTx(mempool.getOrphanPool().get(txID), fullIp);
                }

                node.broadcastTx(txRequest);
            }
        }catch (Exception e){
            System.out.println("Memory Pool" + ":" + e.getMessage());
        }
        }
    }

    @Override
    public void handleBlock(Block blockRequest, StreamObserver<Void> responseObserver)  {
        responseObserver.onNext(Void.getDefaultInstance());
        responseObserver.onCompleted();
        //Make sure one block gets processed each time
        synchronized (this) {
            String blockHash = HashSHA256.hashObject(blockRequest.getHeader());
            if (!node.getChain().has(blockHash)  || node.getChain().getHeight() == -1){
                System.out.println("Block sent to: " + node.getVersion().getIpAddr() + " Hash: " + blockHash);
                node.getChain().getBlockMap().put(blockHash, blockRequest);
                try{
                    //Check if incoming block is extendable to the main chain
                    if(node.getChain().validateHeader(blockRequest)){
                        if(node.getChain().validateBlock(blockRequest)){
                            node.appendBlock(blockRequest);
                            nodeController.sendBlocks();
                            System.out.println(node.getVersion().getIpAddr() + ": added block to main: " + blockHash);
                        }
                    }
                    else{
                        //Check if incoming block is able to create a fork chain
                        if(node.getChain().createFork(blockRequest)){
                            System.out.println(node.getVersion().getIpAddr() + ": Fork created at " + node.getChain().getFork().getHeight());
                        }
                        //Check if incoming block is extendable to the fork chain
                        if(node.getChain().extendFork(blockRequest)){
                            System.out.println(node.getVersion().getIpAddr() + ": added block to fork: " + blockHash);

                            //Reorganise Chain is Fork Chain is taller than Main Chain
                            node.broadcastBlock(blockRequest);
                            if(node.getChain().getFork().getHeight() > node.getChain().getHeight()){
                                ArrayList<Transaction> txReorgList = node.getChain().reorganise();
                                for(Transaction tx : txReorgList){
                                    System.out.println(tx);
                                    node.getPool().addTx(tx);
                                    node.broadcastTx(tx);
                                }
                                node.getPool().getTxPool().clear();
                                node.getPool().getTxIdList().clear();
                                node.getPool().getPoolUTXO().clear();
                                node.resetMining();
                                System.out.println(node.getVersion().getIpAddr() + ": Chain reorganised to height " + node.getChain().getHeight());
                            }
                        }
                        //Store as Orphan Block if block arrived early
                        else if(node.addOrphanBlock(blockRequest)){
                            System.out.println(node.getVersion().getIpAddr() + ": Orphan Block is found and stored");
                        }
                    }
                    //Check if Orphan Block can be inserted to chains
                    if(node.checkOrphan()){
                        System.out.println(node.getVersion().getIpAddr() + ": Orphan Blocks is inserted");
                    }
                    node.getVersion().setHeight(node.getChain().getHeight());
                    System.out.println("Block Height is now: " + node.getChain().getHeight());
                }
                catch(Exception e){
                    System.out.println(node.getVersion().getIpAddr() + ":" + e.getMessage());
                }
            }
        }
    }

    @Override
    public void handshake(Version request, StreamObserver<Version> responseObserver){
        node.addPeer(request);
        node.getPeers().forEach((ipAddress,version)->{
            node.checkIncomingHeight(version);
            node.getVersion().addPeer(ipAddress);
        });
        responseObserver.onNext(node.getVersion().build());
        responseObserver.onCompleted();
    }

    @Override
    public void handleWallet(WalletInfo request, StreamObserver<Void> responseObserver){
        node.getWalletList().add(request.getAddress());
        node.getChain().getWalletMap().put(request.getAddress(),new HashSet<>());
        nodeController.sendWalletList();
        responseObserver.onNext(Void.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void handleBalance(WalletInfo request, StreamObserver<UTXOList> responseObserver){
        UTXOList.Builder userUTXO = UTXOList.newBuilder();
        node.getChain().getUTXO().forEach((k,v)->{
            if(v.getAddress().equals(request.getAddress()) && !node.getPool().getSpentTX().contains(k)){
                UTXO singleUTXO = UTXO.newBuilder().setID(k).setOutput(v).build();
                userUTXO.addUtxos(singleUTXO);
            }
        });
        node.getPool().getPoolUTXO().forEach((k,v)->{
            if(v.getAddress().equals(request.getAddress())){
                UTXO singleUTXO = UTXO.newBuilder().setID(k).setOutput(v).build();
                userUTXO.addUtxos(singleUTXO);
            }
        });
        responseObserver.onNext(userUTXO.build());
        responseObserver.onCompleted();
    }

    public void sendBlock(BlockIndex message, StreamObserver<Void> responseObserver){
        int index = message.getIndex();
        node.sendBlock(node.getChain().getBlockByIndex(index),message.getIpAddr());
        responseObserver.onNext(Void.getDefaultInstance());
        responseObserver.onCompleted();
    }

    public void sendTransaction(TxID message, StreamObserver<TxRes> responseObserver){
        String TxID = message.getTxID();
        if(node.getPool().getTxPool().containsKey(TxID)){
            responseObserver.onNext(TxRes.newBuilder().setFound(true).build());
            responseObserver.onCompleted();
            Transaction tx = node.getPool().getTxPool().get(TxID);
            node.sendTx(tx,message.getIpAddr());
        }
        else{
            responseObserver.onNext(TxRes.newBuilder().setFound(false).build());
            responseObserver.onCompleted();
        }
    }
}
