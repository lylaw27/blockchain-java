package com.example.modular_blockchain.model;

import com.example.modular_blockchain.*;
import com.example.modular_blockchain.Void;
import com.example.modular_blockchain.service.TransactionService;
import com.example.modular_blockchain.helperFunc.HashSHA256;
import com.example.modular_blockchain.helperFunc.MerkleTree;
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
    Server server;
    HashMap<String,Version> peers;
    ArrayList<String> walletList;
    HashMap<String,Wallet> walletMap;

    //Orphan Blocks = ParentHash -> Block
    HashMap<String,Block> orphanBlocks;
    Mempool pool;
    Chain chain;
    Wallet wallet;

    @Setter
    volatile Boolean mining = false;

    public Node(int portNumber){
        startServer(portNumber);
        version = Version.newBuilder().setVersion(1).setHeight(-1).setListenAddr(Integer.toString(portNumber));
        pool = new Mempool();
        chain = new Chain();
        peers = new HashMap<>();
        orphanBlocks = new HashMap<>();
        walletList = new ArrayList<>();
        walletMap = new HashMap<>();
        this.wallet = new Wallet(Integer.toString(portNumber));
        new Thread(this::startMining).start();
    }

    public boolean connect(String peer){
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", Integer.parseInt(peer)).usePlaintext().build();
        NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
        //Prepare Peer List
        peers.forEach((k,v)->{
            version.addPeer(v.getListenAddr());
        });
        try{
            Version peerVer = stub.handshake(version.build());
            addPeer(peerVer);
            checkIncomingHeight(peerVer);
        }
        catch (StatusRuntimeException e){
            System.out.println("Peer does not exist");
            return false;
        }
        finally{
            channel.shutdown();
        }
        return true;
    }

    public void appendBlock(Block block){
        chain.appendBlock(block);
        pool.clear(block.getTransactionsList());
        resetMining();
        broadcastBlock(block);
    }

    public void addPeer(Version peerVer){
        //Add peer to peer list
        peers.putIfAbsent(peerVer.getListenAddr(),peerVer);
        System.out.println(version.getListenAddr() + " connected to peer " + peerVer.getListenAddr());

        //Peer Discovery - connect to other peers in the peer list
        peerVer.getPeerList().forEach(addr->{
        if(!Objects.equals(version.getListenAddr(),addr) && !peers.containsKey(addr)){
            this.connect(addr);
        }});
    }


    public synchronized void startMining() {
        int difficulty = Config.DIFFICULTY.value;
        int nonce = (int)(Math.random()*1000000);
        mining = true;
        String target = new String(new char[difficulty]).replace("\0", "0");
        Transaction coinbaseTx = createCoinbaseTx();
        LinkedList<Transaction> sortedTxList = new LinkedList<>(sortTransactions(pool.getTxPool(),pool.getPoolUTXO()));
        sortedTxList.addFirst(coinbaseTx);
        String merkleRoot = MerkleTree.GenerateRoot(sortedTxList);
        String prevHash = chain.getRecentHash();
        Header.Builder header = Header.newBuilder()
                .setMerkleRoot(merkleRoot)
                .setTimestamp(Instant.now().getEpochSecond())
                .setNonce(nonce)
                .setPrevHash(prevHash)
                .setDifficulty(difficulty);
//        System.out.println(version.getListenAddr() + " is mining ⛏ ...");
        while(mining){
            String attempt = HashSHA256.hashObject(header);
            if(attempt.substring(0,difficulty).equals(target)){
                System.out.println(version.getListenAddr() + " solved: " + attempt + " at height: " + (chain.getHeight()+1) + ", prevHash: " + prevHash);
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

            //Validate block then broadcast it
            try{
                sendBlock(block.build(),Integer.parseInt(version.getListenAddr()));
                broadcastBlock(block.build());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
//        System.out.println(version.getListenAddr() + " stopped mining...");
    }

    public void resetMining(){
        stopMining();
        new Thread(this::startMining).start();
    }

    public void stopMining(){
        mining = false;
    }

    public void broadcastTx(Transaction tx){
        peers.forEach((k,v)-> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", Integer.parseInt(k)).usePlaintext().build();
            NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
            try {
                Void unused = stub.handleTransaction(tx);
            }
            catch (StatusRuntimeException e){
                System.out.println("Connection cannot be established!");
            }
            finally {
                channel.shutdown();
            }
        });
    }

    public void broadcastBlock(Block block){
        peers.forEach((k,v)->{
            sendBlock(block,Integer.parseInt(k));
        });
    }

    public void sendBlock(Block block,int toPort){
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1",toPort).usePlaintext().build();
        NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
//        System.out.println(getVersion().getListenAddr() + "->" + toPort);
        try {
            Void unused = stub.handleBlock(block);
        }
        catch (StatusRuntimeException e){
            System.out.println("Connection cannot be established!");
        }
        finally {
            channel.shutdown();
        }
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

    public void startServer(int portNumber){
        try {
            server = ServerBuilder.forPort(portNumber).addService(new TransactionService(this)).build().start();
            System.out.println("Server started, listening on " + server.getPort());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void disconnectNode(){
        try{
            server.shutdown().awaitTermination();
            server.shutdownNow();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        stopMining();
    }

    public void checkIncomingHeight(Version peerVer){
        if(peerVer.getHeight() > chain.height){
            int index = peerVer.getHeight();
            while(chain.height < peerVer.getHeight() || index > 0){
                ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", Integer.parseInt(peerVer.getListenAddr())).usePlaintext().build();
                NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
                try{
                    BlockIndex message = BlockIndex.newBuilder().setIndex(index).setVersion(version).build();
                    Void unused = stub.requestBlock(message);
                    index--;
                }
                catch (StatusRuntimeException e){
                    System.out.println("Connection cannot be established!");
                }
                finally {
                    channel.shutdown();
                }
            }
        }
    }


    public boolean addOrphanBlock(Block incomingBlock){
        String blockHash = HashSHA256.hashObject(incomingBlock.getHeader());
        if(orphanBlocks.containsKey(blockHash)){
            return false;
        }
        orphanBlocks.put(blockHash,incomingBlock);
        return true;
    }

    public boolean checkOrphan(){
        if(orphanBlocks.isEmpty()){
            return false;
        }
        //Run Block Validations if Orphan Block can be appended
        if(orphanBlocks.containsKey(chain.getRecentHash())){
            Block block = orphanBlocks.get(chain.getRecentHash());
            try{
                //Check if incoming block is extendable to the main chain
                if(chain.validateHeader(block)){
                    if(chain.validateBlock(block)){
                        appendBlock(block);
                        //Recurse to make sure all orphan blocks
                        checkOrphan();
                    }
                }
                //Check if incoming block is extendable to the fork chain
                else if(chain.extendFork(block)){
                    //Reorganise Chain is Fork Chain is taller than Main Chain
                    if(chain.fork.getHeight() > chain.height){
                        chain.reorganise();
                    }
                    checkOrphan();
                }
            }
            catch(Exception e){
                System.out.println("Orphan" + ":" + e.getMessage());
            }
            return true;
        };
        return false;
    }

}