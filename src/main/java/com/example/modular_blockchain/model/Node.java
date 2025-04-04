package com.example.modular_blockchain.model;

import com.example.modular_blockchain.Block;
import com.example.modular_blockchain.Header;
import com.example.modular_blockchain.NodeGrpc;
import com.example.modular_blockchain.Transaction;
import com.example.modular_blockchain.TxInput;
import com.example.modular_blockchain.TxOutput;
import com.example.modular_blockchain.Version;
import com.example.modular_blockchain.Void;
import com.example.modular_blockchain.service.TransactionService;
import io.grpc.*;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class Node {
    Version.Builder version;
    HashMap<String,Version> Peers = new HashMap<>();
    Mempool pool;
    Chain chain;
    Wallet wallet;
    @Setter
    Boolean mining = false;

    public Node(int portNumber) throws IOException {
        version = Version.newBuilder().setVersion(1).setHeight(1).setListenAddr(Integer.toString(portNumber));
        Server server = ServerBuilder.forPort(portNumber).addService(new TransactionService(this)).build();
        pool = new Mempool();
        chain = new Chain();
        this.wallet = new Wallet(Integer.toString(portNumber));
        server.start();
        System.out.println("Server started, listening on " + server.getPort());
    }

    public void connect(String peer){
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", Integer.parseInt(peer)).usePlaintext().build();
        try{
            NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
            Peers.forEach((k,v)->{
                version.addPeerList(v.getListenAddr());
            });
            Version peerVer = stub.handshake(version.build());
            addPeer(peerVer);
        }
        finally{
            channel.shutdown();
        }
    }

    public void addPeer(Version peerVer){
        //Add peer to peer list
        Peers.putIfAbsent(peerVer.getListenAddr(),peerVer);
        System.out.println(version.getListenAddr() + " connected to peer " + peerVer.getListenAddr());

        //Peer Discovery - connect to other peers in the peer list
        peerVer.getPeerListList().forEach(addr->{
        if(!Objects.equals(version.getListenAddr(),addr) && !Peers.containsKey(addr)){
            this.connect(addr);
        }});
    }

    public synchronized void startMining() {
        int difficulty = 4;
        int nonce = (int)(Math.random()*1000000);
        mining = true;
        String target = new String(new char[difficulty]).replace("\0", "0");
        Transaction coinbaseTx = createCoinbaseTx();
        LinkedList<Transaction> sortedTxList = new LinkedList<>(sortTransactions(pool.getTxPool(),pool.getPoolUTXO()));
        sortedTxList.addFirst(coinbaseTx);
        String merkleRoot = MerkleTree.GenerateRoot(sortedTxList);
        String prevHash = chain.getLastHash();
        Header.Builder header = Header.newBuilder()
                .setRootHash(merkleRoot)
                .setTimestamp(Instant.now().getNano())
                .setNonce(nonce)
                .setPrevHash(prevHash)
                .setDifficulty(difficulty);
        System.out.println(version.getListenAddr() + " is mining ⛏ ...");
        while(mining){
            String attempt = HashSHA256.hash(header.toString());
            if(attempt.substring(0,difficulty).equals(target)){
                System.out.println(version.getListenAddr() + " solved: " + header.getNonce());
                break;
            }
            header.setNonce(nonce++);
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        //Broadcast block if this node is first to mine
        if(mining){
            //Build Block
            Block.Builder block = Block.newBuilder().setHeader(header);
            sortedTxList.forEach(block::addTransactions);
            Block newBlock = block.build();

            //Validate block then broadcast it
            if(chain.validateBlock(newBlock)){
                String blockHash = HashSHA256.hash(newBlock.getHeader().toString());
                chain.addBlock(blockHash,newBlock);
                pool.clear(newBlock.getTransactionsList());
                System.out.println(block.getHeader().toString().length());
                resetMining();
                broadcastBlock(block.build());
            }
        }
        System.out.println(version.getListenAddr() + " stopped mining...");
    }

    public void resetMining(){
        mining = false;
        new Thread(this::startMining).start();
    }

    public void broadcastTx(Transaction tx){
        Peers.forEach((k,v)->{
            ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", Integer.parseInt(k)).usePlaintext().build();

            try {
                NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
                Void unused = stub.handleTransaction(tx);
            }
            finally{
                channel.shutdown();
            }
        });
    }

    public void broadcastBlock(Block block){
        Peers.forEach((k,v)->{
            ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", Integer.parseInt(k)).usePlaintext().build();
            NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
            Void unused = stub.handleBlock(block);
            channel.shutdown();
        });
    }

    public Transaction createCoinbaseTx(){
        TxInput input = TxInput.newBuilder()
                .setPrevTxHash("0000000000000000000000000000000000000000000000000000000000000000")
                .setPrevOutIndex(0xFFFFFFFF)
                .setPublicKey(wallet.getPublicKeyString())
                .setCoinbase(true)
                //Since signature is not needed for coinbase Tx, this field is used as extra nonce
                .setSignature(String.valueOf((int)(Math.random()*1000000)))
                .build();
        TxOutput output = TxOutput.newBuilder()
                .setAmount(Config.COINBASE_TX_AMOUNT.value)
                .setAddress(wallet.address)
                .build();
        return Transaction.newBuilder().addInputs(input).addOutputs(output).build();
    }

    public List<Transaction> sortTransactions(HashMap<String,Transaction> txPool,HashMap<String,TxOutput> poolUTXO){
        List<Transaction> sortedTxs = new ArrayList<>();
        HashSet<String> txSet = new HashSet<>();

        //Use a heap to store Transaction Trees and sorted according to fees
        Queue<Deque<String>> txTrees = new PriorityQueue<>((a,b)->{
            AtomicInteger sumA = new AtomicInteger();
            AtomicInteger sumB = new AtomicInteger();
            a.forEach((v)->{
                sumA.addAndGet(txPool.get(v).toString().length());
            });
            b.forEach((v)->{
                sumB.addAndGet(txPool.get(v).toString().length());
            });
            return sumB.get() - sumA.get();
        });

        //Build Transaction Trees and insert to heap
        poolUTXO.forEach((uxtoID,v)->{
            String TxID = uxtoID.substring(0,64);
            Deque<String> txTree = new ArrayDeque<>();
            ancestryTree(txPool,txTree,TxID);
            txTrees.add(txTree);
        });

        //Pop heap to arraylist
        while(!txTrees.isEmpty()){
            Deque<String> txTree = txTrees.poll();
            while(!txTree.isEmpty()){
                String TxID = txTree.pop();
                if(!txSet.contains(TxID)){
                    sortedTxs.add(txPool.get(TxID));
                    txSet.add(TxID);
                }
            }
        }
        txSet.clear();
        return sortedTxs;
    }

    public void ancestryTree(HashMap<String,Transaction> txPool,Deque<String> txTree,String TxID){
        txTree.addFirst(TxID);
        for(TxInput input:txPool.get(TxID).getInputsList()){
            if(txPool.containsKey(input.getPrevTxHash())){
                ancestryTree(txPool,txTree,input.getPrevTxHash());
            }
        }
    }
}