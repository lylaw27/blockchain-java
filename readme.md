# Proof of Work Blockchain in Java

## Overview

A full-featured Proof of Work (PoW) blockchain implementation in Java, designed for learning and experimentation. This project demonstrates modern blockchain principles including:

- Mining with transaction fee prioritization
- Transaction validation via P2PKH
- UTXO transaction model
- Comprehensive block validation
- Mempool for transaction management
- Peer-to-peer communication using gRPC between nodes
- Use of multithreading to process mining and network requests concurrently
- Robust chain reorganization handling
- Blockchain Explorer (Frontend Project) to enquire the current state of the blockchain

## Features

### 1. Node-to-Node Communication (gRPC)
- #### Uses gRPC for efficient node communication
- #### Protocol buffers define message formats for blocks, transactions, and network messages
```proto
syntax = "proto3";

option java_package = "com.example.modular_blockchain";
option java_multiple_files = true;

service Node{
  rpc HandleTransaction(Transaction) returns (Void){};
  rpc Handshake(Version) returns (Version){};
  rpc HandleBlock(Block) returns (Void){};
  rpc HandleBalance(WalletInfo) returns (UTXOList){};
  rpc HandleWallet(WalletInfo) returns (Void){}
  rpc RequestBlock(BlockIndex) returns (Void){};
}

message Version {
  uint32 version = 1;
  int32 height = 2;
  string listenAddr = 3;
  repeated string peer = 4;
}

message Void{ }

message Block{
  Header header = 1;
  repeated Transaction transactions = 2;
}

message Header{
  string prevHash = 1;
  string merkleRoot = 2;
  int64 timestamp = 3;
  uint32 nonce = 4;
  int32 difficulty = 5;
}

message TxInput{
  string prevTxHash = 1;
  optional uint32 prevOutIndex = 2;
  string publicKey = 3;
  string signature = 4;
  optional bool coinbase = 5;
}

message TxOutput{
  optional int64 amount = 1;
  string address = 2;
}

message Transaction {
  string TxID = 1;
  repeated TxInput inputs = 2;
  repeated TxOutput outputs = 3;
}

message UTXOList{
  repeated UTXO utxos = 1;
}

message UTXO{
  TxOutput output = 1;
  string ID = 2;
}

message WalletInfo{
  string address = 1;
}

message BlockIndex{
  Version version = 1;
  int32 index = 2;
}
```

- #### Supports peer discovery and network propagation
```java
//Initiate connection to a node
public boolean connect(String peer){

        //Setup gRPC Channel
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", Integer.parseInt(peer)).usePlaintext().build();
        NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);

        //Prepare Peer List
        peers.forEach((k,v)->{
            version.addPeer(v.getListenAddr());
        });
        try{
            //Attempt to connect to peer
            Version peerVer = stub.handshake(version.build());

            //Add peer to peer list
            addPeer(peerVer);

            //Check if peer has a longer chain, if yes request for blocks
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

    //Add peer to the peer list and connect to other peers in the peer list
    public void addPeer(Version peerVer){

        //Add peer to peer list
        peers.putIfAbsent(peerVer.getListenAddr(),peerVer);
        System.out.println(version.getListenAddr() + " connected to peer " + peerVer.getListenAddr());

        //Peer Discovery - connect to other peers in the peer list
        peerVer.getPeerList().forEach(addr->{

        //Connect to other peers if it is not the same as this node's address and not already in the peer list
        if(!Objects.equals(version.getListenAddr(),addr) && !peers.containsKey(addr)){
            this.connect(addr);
        }});
    }

   //gRPC endpoint for handshake
    @Override
    public void handshake(Version request, StreamObserver<Version> responseObserver){

        //Send information of this node as response 
        responseObserver.onNext(node.getVersion().build());
        responseObserver.onCompleted();

        //Add node that sends incoming request to peer list
        node.addPeer(request);

        //Add all peers of the node that sends incoming request to peer list
        node.getPeers().forEach((k,v)->{
            node.getVersion().addPeer(v.getListenAddr());
        });
    }
```
### 2. Proof Of Work Mining
- #### Make sure the parent of a child transaction must always get mined first
- #### Extract transactions from the mempool based on fees:
```java
//Function to build transaction tree where the parent transaction will be the head of the queue
   public void ancestryTree(HashMap<String,Transaction> txPool,Deque<String> txTree,String TxID){
         //Add transaction to head of queue
         txTree.addFirst(TxID);

         //Loop through all the inputs of the transaction to get parent transaction
         for(TxInput input:txPool.get(TxID).getInputsList()){

            String parentTxID = input.getPrevTxHash();

            //Check if parent transaction is in mempool
            if(txPool.containsKey(parentTxID)){

               //Recurse function with parent transaction
               ancestryTree(txPool,txTree,parentTxID);
            }
        }
    }

//Sort Transactions based on fees
public List<Transaction> sortTransactions(HashMap<String,Transaction> txPool,HashMap<String,TxOutput> poolUTXO){

        List<Transaction> sortedTxs = new ArrayList<>();
        HashSet<String> txSet = new HashSet<>();

        //Use a heap to store Transaction Trees and sorted according to fees
        Queue<Deque<String>> txTrees = new PriorityQueue<>((treeA,treeB)->{

            AtomicInteger feeA = new AtomicInteger();
            AtomicInteger feeB = new AtomicInteger();

            //Calculate fee rate of each transaction tree, Fee Rate = fee/nos. of transaction
            treeA.forEach((txid)->{
                AtomicLong outputAmount = new AtomicLong();
                AtomicLong inputAmount = new AtomicLong();

                //Get output amounts
                txPool.get(txid).getOutputsList().forEach((output)->{
                    outputAmount.addAndGet(output.getAmount());
                });

                //Get input amounts
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

                //Compute Transaction Fees
                feeA.addAndGet((outputAmount.get()-inputAmount.get())/treeA.size());
            });
            
            //Calculate fee rate of each transaction tree, Fee Rate = fee/nos. of transaction
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

                //Compute Transaction Tree fee rate 
                feeB.addAndGet((outputAmount.get()-inputAmount.get())/treeB.size());
            });
            return feeB.get() - feeA.get();
        });

        //First loop through available transactions in the mempool
         txPool.forEach((TxID,tx)->{
            //Create new Transaction Tree
            Deque<String> txTree = new ArrayDeque<>();

            //Call function to build transaction tree
            ancestryTree(txPool,txTree,TxID);
            
            //Add transaction tree to heap
            txTrees.add(txTree);
         });

        //Create final transaction list
        while(!txTrees.isEmpty()){

            //Pop heap to get the highest fee rate transcation tree
            Deque<String> txTree = txTrees.poll();
           
            while(!txTree.isEmpty()){

               //Pop every transaction from the tree and add to the final transaction list for mining
               String TxID = txTree.pop();

               //Make sure no duplicated transactions are added to the list
               if(!txSet.contains(TxID)){
                  sortedTxs.add(txPool.get(TxID));
                  txSet.add(TxID);
                }
            }
        }
        
        return sortedTxs;
    }
```

- #### Generate Merkle Root with the sorted transaction list:
```java
public class MerkleTree {

    //Generate the Merkle root from a list of transactions
    public static String GenerateRoot(List<Transaction> txx ) {
        Deque<String> hashQueue = new ArrayDeque<>();

        //Hash each transaction and add it to the queue
        txx.forEach((tx)->{
            hashQueue.addLast(HashSHA256.hashObject(tx));
        });
        
        return CombineLeaf(hashQueue);
    }

    public static String CombineLeaf(Deque<String> hashQueue) {

        //Return if one leaf is left in the queue
        if(hashQueue.size() == 1){
            return hashQueue.getFirst();
        }

        //Add a copy of the last node to the queue if size is odd number
        if(hashQueue.size()% 2 == 1){
            hashQueue.addLast(hashQueue.peekLast());
        }

        int queueSize = hashQueue.size();
        //Remove pairs of hashes and combine them, then add them back into the queue
        for(int i = 0; i < queueSize/2; i++){
            String firstHash = hashQueue.removeFirst();
            String secondHash = hashQueue.removeFirst();
            hashQueue.addLast(combineHash(firstHash, secondHash));
        }

        //Recurse until one hash is left
        return CombineLeaf(hashQueue);
    }
}
```
- #### Increment nonce on every attempt and hash the block header until the required target is reached
- #### Broadcast the block once mining is completed
- #### Full mining function:
```java
 //We assume every node only has one ongoing mining process
    public synchronized void startMining() {

        //Mining difficulty can be set in Config.java
        int difficulty = Config.DIFFICULTY.value;

        //Start with a random nonce
        int nonce = (int)(Math.random()*1000000);

        //Target is a string of 0s with length of difficulty
        //e.g. difficulty = 4, target = "0000"
        String target = new String(new char[difficulty]).replace("\0", "0");

        //Extract transactions from the mempool based on fees
        LinkedList<Transaction> sortedTxList = new LinkedList<>(sortTransactions(pool.getTxPool(),pool.getPoolUTXO()));
        
        //Create a Coinbase Transaction and add it to the front of the transaction list
        Transaction coinbaseTx = createCoinbaseTx();
        sortedTxList.addFirst(coinbaseTx);

        //Generate Merkle Root with the sorted transaction list
        String merkleRoot = MerkleTree.GenerateRoot(sortedTxList);

        //Get the previous block's hash
        String prevHash = chain.getRecentHash();

        //Build the block header
        Header.Builder header = Header.newBuilder()
                .setMerkleRoot(merkleRoot)
                .setTimestamp(Instant.now().getEpochSecond())
                .setNonce(nonce)
                .setPrevHash(prevHash)
                .setDifficulty(difficulty);

        //Starts mining
        mining = true;
        while(mining){
            
            //Hash the header and check if it meets the target
            String attempt = HashSHA256.hashObject(header);
            if(attempt.substring(0,difficulty).equals(target)){
                break;
            }

            //Increment nonce on every attempt
            header.setNonce(nonce++);

            //Sleep for 1ms to reduce CPU usage
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
                if(chain.validateBlock(blockRequest)){
                    broadcastBlock(block.build());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
```
### 2. Transaction Validation System

- #### P2PKH pattern to validate transaction inputs
- #### Elliptic Curve Digital Signature Algorithm to check for transaction authenticity
- #### Double-spend prevention with UTXO model
- #### Fee verification by checking if output amount is more than input amount
  ```java
  public boolean validateTx(Transaction tx, HashMap<String,TxOutput> tempUTXO) {
        AtomicLong inputAmount = new AtomicLong();
        AtomicLong outputAmount = new AtomicLong();

        //Remove signature field for digital signature verification
        Transaction.Builder unsignedTx = tx.toBuilder().clearInputs();
        tx.getInputsList().forEach(txInput -> {
            unsignedTx.addInputs(txInput.toBuilder().clearSignature().build());
        });

        //Hash Transaction Data for signature verification
        byte[] txData = HashSHA256.hashObject(unsignedTx).getBytes();

        //Validate Every Transaction Input
        for(TxInput input : tx.getInputsList()){
            
            String prevOutputKey = input.getPrevTxHash() + ":" + input.getPrevOutIndex();

            // Check if UTXO exists to prevent double spending
            if(tempUTXO.containsKey(prevOutputKey)){
                inputAmount.addAndGet(tempUTXO.get(prevOutputKey).getAmount());
            }
            else{
                System.out.println("UTXO not found");
                System.out.println(prevOutputKey);
                return false;
            }

            //P2PKH pattern to validate Transaction Inputs
            //Hash public key to get address
            String inputAddress = HashSHA256.hash(input.getPublicKey());

            // Check if UTXO address matches with Transaction Input address
            if(!tempUTXO.get(prevOutputKey).getAddress().equals(inputAddress)){
                System.out.println("Public Key not matching with address!");
                return false;
            }

            // Prepare TxData for verify signature
            String txSign = input.getSignature();

            // Check if signature in TxInput can be unlocked with PubKey
            try {
                Signature sign = Signature.getInstance("SHA256withECDSA");
                sign.initVerify(hexToKey(input.getPublicKey()));
                sign.update(txData);
                if(!sign.verify(Encoder.hexToBytes(txSign))){
                    System.out.println("Invalid Signature");
                    return false;
                }
            } catch (SignatureException | NoSuchAlgorithmException | InvalidKeyException e) {
                throw new RuntimeException(e);
            }
        }

        //Check if Output amount exceeds Input amount
        tx.getOutputsList().forEach(txOutput -> {
            outputAmount.addAndGet(txOutput.getAmount());
        });
        if(inputAmount.get()<outputAmount.get()){
            return false;
        }

        //Remove spent Tx from UTXO set
        tx.getInputsList().forEach(txInput -> {
            String prevOutputKey = txInput.getPrevTxHash() + ":" + txInput.getPrevOutIndex();
            tempUTXO.remove(prevOutputKey);
        });

        //add Tx Outputs to UTXO set if Tx is valid
        String TxID = HashSHA256.hashObject(tx);
        for(int i=0;i<tx.getOutputsList().size();i++){
            String prevOutputKey = TxID + ":" + i;
            tempUTXO.put(prevOutputKey,tx.getOutputsList().get(i));
        }
        return true;
    }
  ```
### 3. Block Validation:
- #### Merkle root validation
- #### Proof of Work difficulty verification
- #### Timestamp validation
- #### Coinbase Transaction validation
- #### Validate all transactions in the block
```java
public boolean validateBlock(Block incomingBlock) throws Exception {
        //Create a Copy of temporary tx storage for validation
        String newBlockHash = HashSHA256.hashObject(incomingBlock.getHeader());
        LinkedList<Transaction> txList = new LinkedList<>(incomingBlock.getTransactionsList());

        //Check if Merkle Root is Valid
        String merkleRoot = MerkleTree.GenerateRoot(txList);
        if(!incomingBlock.getHeader().getMerkleRoot().equals(merkleRoot)){
            throw new Exception("Invalid Merkle Root!");
        }

        //Check if Block difficulty is valid
        if(!incomingBlock.getHeader().getDifficulty().equals(Config.DIFFICULTY.value)){
            throw new Exception("Invalid Block Difficulty!");
        }

        //Check if Block timestamp is larger than previous block timestamp and smaller than current time
        if(height != -1 && incomingBlock.getHeader().getTimestamp() <= blockList.getLast().getHeader().getTimestamp() && 
        incomingBlock.getHeader().getTimestamp() > Instant.now().getEpochSecond()){
            throw new Exception("Invalid Block Timestamp!");
        }

        //Check if coinbase Tx is valid
        Transaction coinbaseTx = txList.removeFirst();
        if(!coinbaseTx.getInputs(0).getCoinbase() && coinbaseTx.getOutputs(0).getAmount() != Config.COINBASE_TX_AMOUNT.value){
            throw new Exception("Invalid Coinbase Tx!");
        }

        //Iterate and Validate all Tx
        HashMap<String,TxOutput> tempUTXO = new HashMap<>(UTXO);
        for(Transaction tx:txList){
            if(!validateTx(tx,tempUTXO)){
                throw new Exception("Invalid TXs!");
            }
        }

        // Add coinbase Tx back to Tx List
        txList.addFirst(coinbaseTx);

        //if block is valid, update the Transaction map + UTXO
        storeTransaction(newBlockHash,txList);
        return true;
    }
```
### 4. Chain Reorganization
- #### Handles forks in the blockchain:
```java
//Create a new fork if the incoming block can point to blocks in the main chain
    public boolean createFork(Block incomingBlock){

        String prevBlockHash = incomingBlock.getHeader().getPrevHash();

        //Check new Block's previous hash can point to blocks in the main chain
        if(blockMap.containsKey(prevBlockHash)){
            int forkHeight = getBlockHeight(prevBlockHash);
            
            //Incoming Block beyond 6 confirmed blocks will be rejected
            if(height - forkHeight < 6){
                
                //Make a copy of UTXO and rollback to fork height
                HashMap<String, TxOutput> forkUTXO = new HashMap<>(rollbackUTXO(height,forkHeight,new HashMap<>(UTXO)));
                
                //Create Fork and return true
                fork = new Chain(forkHeight,forkUTXO,blockMap.get(prevBlockHash));
                return true;
            }
        }

        //If no valid fork can be created, return false
        return false;
    }

    public boolean extendFork(Block incomingBlock){

        //Validate block in fork chain if there is any
        if(fork != null){
            try{
                if(fork.validateHeader(incomingBlock) && fork.validateBlock(incomingBlock)){
                    fork.appendBlock(incomingBlock);
                    return true;
                }
            }
            catch(Exception e){
                System.out.println(e.getMessage());
            }
        }
        return false;
    }
```
- #### Algorithm to rollback UTXO storage:
```java
//Reverse UTXO to desired block height
    public HashMap<String, TxOutput> rollbackUTXO(int currHeight,int targetHeight,HashMap<String, TxOutput> prevUTXO){
        
        //Rollback is completed if current block height reaches target block height
        if(currHeight<=targetHeight){
            return prevUTXO;
        }

        Block block = blockList.get(currHeight);
        List<Transaction> txList = block.getTransactionsList();

        //Revert UTXO for all transactions in the current block
        for(Transaction tx:txList){
            String TxID = HashSHA256.hashObject(tx);
            for(TxInput input:tx.getInputsList()){
                String UTXOkey = input.getPrevTxHash() + ":" + input.getPrevOutIndex();

                //Add to back UTXOs for outputs outside of current block
                if(!input.getCoinbase() && txMap.containsKey(input.getPrevTxHash())){
                    String txKey = txMap.get(input.getPrevTxHash());
                    Block prevBlock = blockMap.get(txKey.substring(0,64));
                    Transaction prevTx = prevBlock.getTransactions(Integer.parseInt(txKey.substring(65)));
                    TxOutput prevOutput = prevTx.getOutputs(input.getPrevOutIndex());
                    prevUTXO.put(UTXOkey,prevOutput);
                }
            }

            //Remove all UTXOs for outputs in the current block
            for(int i=0;i<tx.getOutputsList().size();i++){
                String key = TxID + ":" + i;
                prevUTXO.remove(key);
            }
        }

        //Recursively rollback to desired block height
        return rollbackUTXO(currHeight-1,targetHeight,prevUTXO);
    }
```
- #### Automatically switches to the longest valid chain when fork is longer:
```java
public ArrayList<Transaction> reorganise(){

        int forkStartHeight = fork.getHeight() - fork.getBlockList().size()+1;

        ArrayList<Transaction> txReorgList = new ArrayList<>();

        //Update UTXO storage with that of fork's
        UTXO = fork.getUTXO();

        //Remove outdated transactions + blocks
        for(int i = height;i>forkStartHeight;i--){
            Block block = removeBlock(i);
            for(Transaction tx:block.getTransactionsList()){
                String TxID = HashSHA256.hashObject(tx);
                txMap.remove(TxID);
                if(!tx.getInputs(0).getCoinbase()){
                    txReorgList.add(tx);
                }
            }
        }

        //Insert fork blocks and transactions to main chain
        for(int i = 1;i<fork.getBlockList().size();i++){
            Block block = fork.getBlockList().get(i);
            try {
                if(validateHeader(block) && validateBlock(block)){
                    appendBlock(block);
                }
            } catch (Exception e) {
                System.out.println("Chain" + ":" + e.getMessage());
            }
            for(int j=0;j<block.getTransactionsList().size();j++){
                txReorgList.remove(block.getTransactions(j));
            }
        }

        fork = null;

        //Return any extra transactions from stale blocks for re-mining
        return txReorgList;
    }
```
- #### Store orphan blocks if can't connect to any chain
- #### Check if orphan blocks can be reconected to the chain after parent block arrives late:
```java
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
    public boolean addOrphanBlock(Block incomingBlock){
        String blockHash = HashSHA256.hashObject(incomingBlock.getHeader());
        if(orphanBlocks.containsKey(blockHash)){
            return false;
        }
        orphanBlocks.put(blockHash,incomingBlock);
        return true;
    }
```
- #### Here is the gRPC endpoint that handles incoming blocks:
```java
@Override
    public void handleBlock(Block blockRequest, StreamObserver<Void> responseObserver)  {

        responseObserver.onNext(Void.getDefaultInstance());
        responseObserver.onCompleted();

        //Make sure one block gets processed each time
        synchronized (this) {
            
            //Hash the block header to get the block hash
            String blockHash = HashSHA256.hashObject(blockRequest.getHeader());
            
            //Check if the block is already in the chain
            if (!node.getChain().has(blockHash)){
                node.getChain().getBlockMap().put(blockHash, blockRequest);
                try{

                    //Check if incoming block is extendable to the main chain
                    if(node.getChain().validateHeader(blockRequest)){
                        if(node.getChain().validateBlock(blockRequest)){
                            node.appendBlock(blockRequest);
                            wsController.sendBlocks(node);
                        }
                    }
                    else{

                        //Check if incoming block is able to create a fork chain
                        if(node.getChain().createFork(blockRequest)){
                            System.out.println(node.getVersion().getListenAddr() + ": Fork created at " + node.getChain().getFork().getHeight());
                        }

                        //Check if incoming block is extendable to the fork chain
                        if(node.getChain().extendFork(blockRequest)){
                            System.out.println(node.getVersion().getListenAddr() + ": added block to fork: " + blockHash);

                            //Reorganise Chain is Fork Chain is taller than Main Chain
                            node.broadcastBlock(blockRequest);
                            if(node.getChain().getFork().getHeight() > node.getChain().getHeight()){
                                ArrayList<Transaction> txReorgList = node.getChain().reorganise();
                                //Add back unmined transactions to Mempool and broadcast them
                                for(Transaction tx : txReorgList){
                                    System.out.println(tx);
                                    node.getPool().addTx(tx);
                                    node.broadcastTx(tx);
                                }

                                node.resetMining();
                                System.out.println(node.getVersion().getListenAddr() + ": Chain reorganised to height " + node.getChain().getHeight());
                            }
                        }
                        //Store as Orphan Block if block arrived early
                        else if(node.addOrphanBlock(blockRequest)){
                            System.out.println(node.getVersion().getListenAddr() + ": Orphan Block is found and stored");
                        }
                    }

                    //Check if Orphan Block can be inserted to chains
                    if(node.checkOrphan()){
                        System.out.println(node.getVersion().getListenAddr() + ": Orphan Blocks is inserted");
                    }
                    node.getVersion().setHeight(node.getChain().getHeight());
                }
                catch(Exception e){
                    System.out.println(node.getVersion().getListenAddr() + ":" + e.getMessage());
                }
            }
        }
    }
```

## Prerequisites
- Java JDK 17 or higher
- Maven 3.6+
- Protobuf compiler (protoc)
- gRPC Java libraries

## Installation
1. Clone the repository:
   bash
   git clone https://github.com/lylaw27/blockchain-java.git
   cd blockchain-java


2. Build the project:
   ```bash
   mvn clean install
   ```

3. Generate gRPC code:
   ```bash
   mvn compile
   ```

## Configuration
Edit config.properties to configure:
- Network port
- Peer connections
- Mining difficulty
- Block time target
- Mempool settings

Example configuration:
properties
node.port=50051
peer.nodes=192.168.1.2:50051,192.168.1.3:50051
mining.difficulty=18
block.target.time=60000
mempool.max.size=10000


## Running the Node
Start a node with:
bash
mvn exec:java -Dexec.mainClass="com.blockchain.NodeMain"


### Command Line Options
- --mine: Enable mining mode
- --port: Specify custom port
- --peers: Override configured peers
- --genesis: Initialize new genesis block

## API Documentation
The node exposes gRPC services for:
- Submitting transactions
- Submitting blocks
- Querying blockchain state
- Querying wallet balance
- Peer status

See src/main/proto/Node.proto for service definitions.


## Contributing
1. Fork the repository
2. Create a feature branch
3. Submit a pull request

## License
MIT License

## Future Work
- Wallet integration
- Smart contract support
- Performance optimizations
- Additional consensus mechanisms

## Contact
For questions or issues, please open a GitHub issue or contact maintainers.
