package com.example.modular_blockchain.model;
import com.example.modular_blockchain.Block;
import com.example.modular_blockchain.Transaction;
import com.example.modular_blockchain.TxInput;
import com.example.modular_blockchain.TxOutput;
import lombok.Getter;

import java.security.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.example.modular_blockchain.model.Encoder.hexToKey;

@Getter
public class Chain {
    HashMap<String, TxOutput> UTXO = new HashMap<>();
    HashMap<String,Block> blockMap = new HashMap<>();
    ArrayList<Block> blockList = new ArrayList<>();
    int height = -1;

    public Block getBlockByIndex(int index){
        return blockList.get(index);
    }

    public boolean has(String hash) {
        return blockMap.containsKey(hash);
    }

    public void addBlock(String hash, Block block) {
        blockMap.put(hash,block);
        blockList.add(block);
        height++;
    }

    public String getLastHash() {
        if(height == -1) return "0000000000000000000000000000000000000000000000000000000000000000";
        return HashSHA256.hash(getBlockByIndex(height).getHeader().toString());
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
        byte[] txData = HashSHA256.hash(unsignedTx.toString()).getBytes();

        //Validate Every Transaction Input
        for(TxInput input : tx.getInputsList()){
            String inputAddress = HashSHA256.hash(input.getPublicKey());
            String prevOutputKey = input.getPrevTxHash() + ":" + input.getPrevOutIndex();
            // Check if UTXO exists to prevent double spending
            // Check if UTXO address matches with new TxInput address
            if(tempUTXO.containsKey(prevOutputKey) && tempUTXO.get(prevOutputKey).getAddress().equals(inputAddress)){
                inputAmount.addAndGet(tempUTXO.get(prevOutputKey).getAmount());
            }
            else{
                System.out.println("UTXO not found");
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
        return true;
    }

    public boolean validateBlock(Block incomingBlock){
        //Create a Copy of temporary UTXO set
        HashMap<String,TxOutput> tempUTXO = new HashMap<>(UTXO);

        //Check if new block prevHash matches with the current block hash
        if(!(height == -1 || incomingBlock.getHeader().getPrevHash().equals(getLastHash()))){
            System.out.println(incomingBlock.getHeader().getPrevHash());
            System.out.println("Invalid Previous Block Hash!");
            return false;
        }

        LinkedList<Transaction> txList = new LinkedList<>(incomingBlock.getTransactionsList());

        //Check if Merkle Root is Valid
        String merkleRoot = MerkleTree.GenerateRoot(txList);
        if(!incomingBlock.getHeader().getRootHash().equals(merkleRoot)){
            System.out.println("Invalid Merkle Root!");
            return false;
        }

        //Check if coinbase Tx is valid
        Transaction coinbaseTx = txList.removeFirst();
        if(!coinbaseTx.getInputs(0).getCoinbase() && coinbaseTx.getOutputs(0).getAmount() != Config.COINBASE_TX_AMOUNT.value){
            System.out.println("Invalid Coinbase Tx!");
            return false;
        }

        //Iterate and Validate all Tx
        for(Transaction tx:txList){
            if(!validateTx(tx,tempUTXO)){
                System.out.println("Invalid TXs!");
                return false;
            }

            //add Tx Outputs to UTXO set if Tx is valid
            String TxID = HashSHA256.hash(tx.toString());
            for(int i=0;i<tx.getOutputsList().size();i++){
                String prevOutputKey = TxID + ":" + i;
                tempUTXO.put(prevOutputKey,tx.getOutputsList().get(i));
            }
        }

        //Insert coinbase Tx into UTXO set (assume coinbase Tx can only be used after block is mined)
        String coinbaseID = HashSHA256.hash(coinbaseTx.toString());
        tempUTXO.put(HashSHA256.hash(coinbaseID)+":"+0,coinbaseTx.getOutputs(0));

        //if block is valid then we update the UTXO
        UTXO = tempUTXO;
        return true;
    }
}
