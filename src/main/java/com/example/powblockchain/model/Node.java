package com.example.powblockchain.model;

import com.example.powblockchain.*;
import com.example.powblockchain.Void;
import com.example.powblockchain.helperFunc.PublicIpResolver;
import com.example.powblockchain.service.TransactionService;
import com.example.powblockchain.helperFunc.HashSHA256;
import com.example.powblockchain.helperFunc.MerkleTree;
import io.grpc.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Getter
@Component
public class Node {
    Version.Builder version;
    Server server;
    String serverIp;
    HashMap<String,Version> peers;
    ArrayList<String> walletList;
    HashMap<String,Wallet> walletMap;

    //Orphan Blocks = ParentHash -> Block
    HashMap<String,Block> orphanBlocks;
    Mempool pool;
    Wallet wallet;

//    @Lazy
//    @Autowired
//    TransactionService service;

    @Autowired
    Chain chain;

    @Value("${node.id}")
    String nodeId;

    int port;

    @Value("${mining.difficulty}")
    int defaultDifficulty;

    @Value("${coinbase.amount}")
    long coinbaseAmount;

    @Setter
    volatile Boolean mining = false;

    public Node(@Value("${grpc.port}") int port, @Value("${spring.profiles.active}") String env){
        this.port = port;
        if(env.equals("dev")){
            serverIp = "127.0.0.1"+":"+port;
        }
        else{
            serverIp = PublicIpResolver.getPublicIp()+":"+port;
        }
        version = Version.newBuilder().setVersion(1).setHeight(-1).setListenAddr(serverIp);
        pool = new Mempool();
//        chain = new Chain();
        peers = new HashMap<>();
        orphanBlocks = new HashMap<>();
        walletList = new ArrayList<>();
        walletMap = new HashMap<>();
        this.wallet = new Wallet();
    }

    public boolean connect(String ipAddress){
        String[] ipAndPort = ipAddress.split(":");
        ManagedChannel channel = ManagedChannelBuilder.forAddress(ipAndPort[0], Integer.parseInt(ipAndPort[1])).usePlaintext().build();
        NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
        System.out.println("Connecting to " + ipAndPort[0] + ":" + ipAndPort[1]);

        //Prepare Peer List
        peers.forEach((ip,v)->{
            version.addPeer(ip);
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
        int difficulty = defaultDifficulty;
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
                .setTimestamp(Instant.now().toEpochMilli())
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
                sendBlock(block.build(),version.getListenAddr());
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
        peers.forEach((ipAddress,v)-> {
            sendTx(tx,ipAddress);
        });
    }

    public void sendTx(Transaction tx,String ipAddress){
        String[] ipAndPort = ipAddress.split(":");
        ManagedChannel channel = ManagedChannelBuilder.forAddress(ipAndPort[0], Integer.parseInt(ipAndPort[1])).usePlaintext().build();
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
    }

    public void broadcastBlock(Block block){
        peers.forEach((ipAddress,v)->{
            sendBlock(block,ipAddress);
        });
    }

    public void sendBlock(Block block,String ipAddress){
        System.out.println(ipAddress);
        String[] ipAndPort = ipAddress.split(":");
        ManagedChannel channel = ManagedChannelBuilder.forAddress(ipAndPort[0], Integer.parseInt(ipAndPort[1])).usePlaintext().build();
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
                .setAmount(coinbaseAmount)
                .setAddress(wallet.address)
                .build();
        return Transaction.newBuilder().addInputs(input).addOutputs(output).build();
    }

    public List<Transaction> sortTransactions(HashMap<String,Transaction> txPool,HashMap<String,TxOutput> poolUTXO){
        List<Transaction> sortedTxs = new ArrayList<>();
        HashSet<String> txSet = new HashSet<>();

        //Use a heap to store Transaction Trees and sorted according to fees
        Queue<Deque<String>> txTrees = new PriorityQueue<>((treeA,treeB)->{
            AtomicInteger feeA = new AtomicInteger();
            AtomicInteger feeB = new AtomicInteger();

            //Calculate the total fee of each transaction tree
            treeA.forEach((txid)->{
                AtomicLong outputAmount = new AtomicLong();
                AtomicLong inputAmount = new AtomicLong();
                txPool.get(txid).getOutputsList().forEach((output)->{
                    outputAmount.addAndGet(output.getAmount());
                });
                txPool.get(txid).getInputsList().forEach((input)->{
                    if(txPool.containsKey(input.getPrevTxHash())){
                        inputAmount.addAndGet(txPool.get(input.getPrevTxHash()).getOutputs(input.getPrevOutIndex()).getAmount());
                    }
                    if(chain.getTxMap().containsKey(input.getPrevTxHash())){
                        String TxLocation = chain.getTxMap().get(input.getPrevTxHash());
                        String blockHash = TxLocation.substring(0,64);
                        int TxIndex = Integer.parseInt(TxLocation.substring(65));
                        Block block = chain.getBlockMap().get(blockHash);
                        Transaction tx = block.getTransactions(TxIndex);
                        inputAmount.addAndGet(tx.getOutputs(input.getPrevOutIndex()).getAmount());
                    }
                });
                feeA.addAndGet(txPool.get(txid).toString().length());
            });

            treeB.forEach((txid)->{
                AtomicLong outputAmount = new AtomicLong();
                AtomicLong inputAmount = new AtomicLong();
                txPool.get(txid).getOutputsList().forEach((output)->{
                    outputAmount.addAndGet(output.getAmount());
                });
                txPool.get(txid).getInputsList().forEach((input)->{
                    if(txPool.containsKey(input.getPrevTxHash())){
                        inputAmount.addAndGet(txPool.get(input.getPrevTxHash()).getOutputs(input.getPrevOutIndex()).getAmount());
                    }
                    if(chain.getTxMap().containsKey(input.getPrevTxHash())){
                        String TxLocation = chain.getTxMap().get(input.getPrevTxHash());
                        String blockHash = TxLocation.substring(0,64);
                        int TxIndex = Integer.parseInt(TxLocation.substring(65));
                        Block block = chain.getBlockMap().get(blockHash);
                        Transaction tx = block.getTransactions(TxIndex);
                        inputAmount.addAndGet(tx.getOutputs(input.getPrevOutIndex()).getAmount());
                    }
                });
                feeB.addAndGet(txPool.get(txid).toString().length());
            });
            return feeB.get() - feeA.get();
        });

        //Build Transaction Trees and insert to heap
        poolUTXO.forEach((utxoID,output)->{
            String TxID = utxoID.substring(0,64);
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

    public void startServer(){
        try {
            server = ServerBuilder.forPort(port)
                    .addService(new TransactionService(this))
                    .build()
                    .start();
            System.out.println("Server started, listening on " + server.getPort());
            wallet.connect(serverIp);
            new Thread(this::startMining).start();

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
                String[] ipAndPort = peerVer.getListenAddr().split(":");
                ManagedChannel channel = ManagedChannelBuilder.forAddress(ipAndPort[0], Integer.parseInt(ipAndPort[1])).usePlaintext().build();
                NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
                try{
                    BlockIndex message = BlockIndex.newBuilder().setIndex(index).setVersion(version).build();
                    Void unused = stub.sendBlock(message);
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