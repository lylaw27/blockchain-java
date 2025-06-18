package com.example.powblockchain.model;
import com.example.powblockchain.Block;
import com.example.powblockchain.Transaction;
import com.example.powblockchain.TxInput;
import com.example.powblockchain.TxOutput;
import com.example.powblockchain.helperFunc.Encoder;
import com.example.powblockchain.helperFunc.HashSHA256;
import com.example.powblockchain.helperFunc.MerkleTree;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.example.powblockchain.helperFunc.Encoder.hexToKey;

@Getter
@Component
public class Chain {
    int height;
    //UTXO index = TxID:TxOutputIndex -> UTXO
    HashMap<String, TxOutput> UTXO = new HashMap<>();

    //Tx index = TxID -> BlockHash:TxIndex
    HashMap<String,String> txMap = new HashMap<>();

    //Wallet index = address -> TxID
    HashMap<String, HashSet<String>> walletMap = new HashMap<>();

    //Store all blocks to track if block has been sent to this node
    HashMap<String,Block> blockMap = new HashMap<>();

    //Blockchain
    ArrayList<Block> blockList = new ArrayList<>();

    //Fork
    Chain fork;

    @Value("${coinbase.amount}")
    long coinbaseAmount;

    public Chain(){this.height = -1;}

    //Construct fork
    public Chain(int height,HashMap<String, TxOutput> UTXO,Block block){
        this.UTXO = new HashMap<>(UTXO);
        //add block that starts the fork
        appendBlock(block);
        this.height = height;
    }

    public Block getBlockByIndex(int index){
        return blockList.get(index);
    }

    public int getBlockHeight(String blockHash){return blockList.indexOf(blockMap.get(blockHash));}

    public boolean has(String hash) {
        return blockMap.containsKey(hash);
    }

    public void appendBlock(Block block) {
        String hash = HashSHA256.hashObject(block.getHeader());
        blockMap.put(hash,block);
        blockList.add(block);
        height++;
    }

    public Block removeBlock(int idx) {
        Block block = blockList.remove(idx);
        String hash = HashSHA256.hashObject(block.getHeader());
        blockMap.remove(hash);
        height--;
        return block;
    }

    public String getRecentHash() {
        if(height == -1) return "0000000000000000000000000000000000000000000000000000000000000000";
        return HashSHA256.hashObject(blockList.getLast().getHeader());
    }

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
            String inputAddress = HashSHA256.hash(input.getPublicKey());
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

            // Check if UTXO address matches with new TxInput address
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

    public boolean validateHeader(Block incomingBlock){
        //Check if new block prevHash matches with the current block hash of main chain
        String prevBlockHash = incomingBlock.getHeader().getPrevHash();
        return height == -1 || prevBlockHash.equals(getRecentHash());
    }

    public boolean validateBlock(Block incomingBlock) throws Exception {
        //Create a Copy of temporary tx storage for validation
        String newBlockHash = HashSHA256.hashObject(incomingBlock.getHeader());
        LinkedList<Transaction> txList = new LinkedList<>(incomingBlock.getTransactionsList());

        //Check if Merkle Root is Valid
        String merkleRoot = MerkleTree.GenerateRoot(txList);
        if(!incomingBlock.getHeader().getMerkleRoot().equals(merkleRoot)){
            throw new Exception("Invalid Merkle Root!");
        }

        //Check if coinbase Tx is valid
        Transaction coinbaseTx = txList.removeFirst();
        if(!coinbaseTx.getInputs(0).getCoinbase() && coinbaseTx.getOutputs(0).getAmount() != coinbaseAmount){
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

    //Store and index Transactions once block is added and validated
    public void storeTransaction(String BlockHash, List<Transaction> txList){
        int txCount = 0;
        for(Transaction tx:txList){
            String TxID = HashSHA256.hashObject(tx);

            for(TxInput txInput:tx.getInputsList()){
                //Remove UTXOs that are unlocked by Tx input
                String prevOutputKey = txInput.getPrevTxHash() + ":" + txInput.getPrevOutIndex();
                UTXO.remove(prevOutputKey);

                //Add Tx to Wallet Map
                String address = HashSHA256.hash(txInput.getPublicKey());
                walletMap.putIfAbsent(address,new HashSet<>());
                walletMap.get(address).add(TxID);
            }

            for(int i=0;i<tx.getOutputsList().size();i++){
                //add Tx Outputs to UTXO set if Tx is valid
                String prevOutputKey = TxID + ":" + i;
                UTXO.put(prevOutputKey,tx.getOutputsList().get(i));

                //Add Tx to Wallet Map
                String address = tx.getOutputsList().get(i).getAddress();
                walletMap.putIfAbsent(address,new HashSet<>());
                walletMap.get(tx.getOutputsList().get(i).getAddress()).add(TxID);
            }

            //add Tx to Tx set if Tx is valid
            String TxIndex = BlockHash + ":" + txCount;
            txMap.put(TxID,TxIndex);
            txCount++;
        }
    }

    //Reverse UTXO to desired block height
    public HashMap<String, TxOutput> rollbackUTXO(int currHeight,int targetHeight,HashMap<String, TxOutput> UTXO){
        if(currHeight<=targetHeight){
            return UTXO;
        }
        HashMap<String, TxOutput> prevUTXO = new HashMap<>(UTXO);
        Block block = blockList.get(currHeight);
        List<Transaction> txList = block.getTransactionsList();
        for(Transaction tx:txList){
            String TxID = HashSHA256.hashObject(tx);
            for(TxInput input:tx.getInputsList()){
                String UTXOkey = input.getPrevTxHash() + ":" + input.getPrevOutIndex();
                //add to back UTXOs for outputs outside of current block
                if(!input.getCoinbase() && txMap.containsKey(input.getPrevTxHash())){
                    String txKey = txMap.get(input.getPrevTxHash());
                    Block prevBlock = blockMap.get(txKey.substring(0,64));
                    Transaction prevTx = prevBlock.getTransactions(Integer.parseInt(txKey.substring(65)));
                    TxOutput prevOutput = prevTx.getOutputs(input.getPrevOutIndex());
                    prevUTXO.put(UTXOkey,prevOutput);
                }
            }
            for(int i=0;i<tx.getOutputsList().size();i++){
                String key = TxID + ":" + i;
                prevUTXO.remove(key);
            }
        }
        return rollbackUTXO(currHeight-1,targetHeight,prevUTXO);
    }

    public boolean createFork(Block incomingBlock){
        String prevBlockHash = incomingBlock.getHeader().getPrevHash();

        //If new Block's previous hash can point to blocks in the main chain, then forking would occur
        if(blockMap.containsKey(prevBlockHash)){
            int forkHeight = getBlockHeight(prevBlockHash);
            //Incoming Block beyond 6 confirmed blocks will be rejected
            if(height - forkHeight < 6){
                //Create Fork, then request blocks if other nodes have a taller chain
                fork = new Chain(forkHeight,rollbackUTXO(height,forkHeight,UTXO),blockMap.get(prevBlockHash));
                return true;
            }
        }
        return false;
    }

    public boolean extendFork(Block incomingBlock){
//        String prevBlockHash = incomingBlock.getHeader().getPrevHash();

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

    public ArrayList<Transaction> reorganise(){
        int forkStartHeight = fork.getHeight()-fork.getBlockList().size()+1;

        //Return any extra transactions from stale blocks for re-mining
        ArrayList<Transaction> txReorgList = new ArrayList<>();

        UTXO = rollbackUTXO(height,forkStartHeight,UTXO);

        //Remove Tx Set + blocks
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
        //Insert fork blocks and tx set to main chain
        for(int i = 1;i<fork.getBlockList().size();i++){
            Block block = fork.getBlockList().get(i);
            try {
                if(validateHeader(block) && validateBlock(block)){
                    appendBlock(block);
                }
            } catch (Exception e) {
                System.out.println("Chain" + ":" + e.getMessage());
            }
//            String blockHash = HashSHA256.hashObject(block.getHeader());
            for(int j=0;j<block.getTransactionsList().size();j++){
//                String TxID = HashSHA256.hashObject(block.getTransactions(j));
//                String txIndex = blockHash + ":" + j;
//                txMap.put(TxID,txIndex);
                txReorgList.remove(block.getTransactions(j));
            }
        }
//        UTXO = fork.getUTXO();
        fork = null;
//        height = blockList.size()-1;
        return txReorgList;
    };
}
