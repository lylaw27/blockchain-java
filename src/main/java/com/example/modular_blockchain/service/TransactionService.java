package com.example.modular_blockchain.service;

import com.example.modular_blockchain.*;
import com.example.modular_blockchain.Void;
import com.example.modular_blockchain.model.HashSHA256;
import com.example.modular_blockchain.model.Mempool;
import com.example.modular_blockchain.model.Node;
import io.grpc.stub.StreamObserver;

import java.util.Objects;

public class TransactionService extends NodeGrpc.NodeImplBase {
    Node node;
    public TransactionService(Node node) {
        this.node = node;
    }

    @Override
    public void handleTransaction(Transaction txRequest, StreamObserver<Void> responseObserver) {
        String txID = HashSHA256.hash(txRequest.toString());
        Mempool mempool = node.getPool();
        if (!mempool.has(txID) && mempool.validateTx(txRequest,node.getChain().getUTXO())) {
//            System.out.println("tx sent to: " + node.getVersion().getListenAddr() + " Hash: " + txID);
            node.getPool().addTx(txRequest);
            node.broadcastTx(txRequest);
        }
        responseObserver.onNext(Void.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void handleBlock(Block blockRequest, StreamObserver<Void> responseObserver)  {
        String blockHash = HashSHA256.hash(blockRequest.getHeader().toString());
        if (!node.getChain().has(blockHash) && node.getChain().validateBlock(blockRequest)) {
            System.out.println("Block sent to: " + node.getVersion().getListenAddr() + " Hash: " + blockHash);
            node.getChain().addBlock(blockHash,blockRequest);
            node.getPool().clear(blockRequest.getTransactionsList());
            node.resetMining();
            node.broadcastBlock(blockRequest);
        }
        responseObserver.onNext(Void.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void handshake(Version request, StreamObserver<Version> responseObserver){
        node.addPeer(request);
        node.getPeers().forEach((k,v)->{
            node.getVersion().addPeerList(v.getListenAddr());
        });
        responseObserver.onNext(node.getVersion().build());
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
}
