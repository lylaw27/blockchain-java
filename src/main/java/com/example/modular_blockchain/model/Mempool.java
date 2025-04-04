package com.example.modular_blockchain.model;

import com.example.modular_blockchain.Transaction;
import com.example.modular_blockchain.TxInput;
import com.example.modular_blockchain.TxOutput;
import lombok.Getter;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.example.modular_blockchain.model.Encoder.*;

@Getter
public class Mempool {
    HashMap<String, Transaction> txPool;
    HashMap<String, TxOutput> poolUTXO;
    HashSet<String> spentTX;

    public Mempool() {
        txPool = new HashMap<>();
        poolUTXO = new HashMap<>();
        spentTX = new HashSet<>();
    }

    public void addTx(Transaction tx) {
        String TxID = HashSHA256.hash(tx.toString());
        txPool.put(TxID,tx);
        for(int i=0;i<tx.getOutputsList().size();i++){
            String prevOutputKey = TxID + ":" + i;
            poolUTXO.put(prevOutputKey,tx.getOutputsList().get(i));
        }
    }

    public boolean validateTx(Transaction tx,HashMap<String,TxOutput> confirmedUTXO) {
        AtomicLong inputAmount = new AtomicLong();
        AtomicLong outputAmount = new AtomicLong();
        if(tx.getInputsCount() < 1 || tx.getOutputsCount() < 1) {
            System.out.println(tx);
            System.out.println("Incomplete Tx data!");
            return false;
        }
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
            if(!spentTX.contains(prevOutputKey) && confirmedUTXO.containsKey(prevOutputKey) && confirmedUTXO.get(prevOutputKey).getAddress().equals(inputAddress)){
                inputAmount.addAndGet(confirmedUTXO.get(prevOutputKey).getAmount());
            }
            else{
                if(poolUTXO.containsKey(prevOutputKey) && poolUTXO.get(prevOutputKey).getAddress().equals(inputAddress)){
                    inputAmount.addAndGet(poolUTXO.get(prevOutputKey).getAmount());
                }
                else{
                    System.out.println("UTXO not found!");
                    return false;
                }
            }

            // Prepare TxData for verify signature
            String txSign = input.getSignature();

            // Check if signature in TxInput can be unlocked with PubKey
            try {
                Signature sign = Signature.getInstance("SHA256withECDSA");
                sign.initVerify(hexToKey(input.getPublicKey()));
                sign.update(txData);
                if(!sign.verify(Encoder.hexToBytes(txSign))){
                    System.out.println("Invalid signature!");
                    return false;
                }
            } catch (SignatureException | NoSuchAlgorithmException | InvalidKeyException e) {
                throw new RuntimeException(e);
            }
        }
        //Check if Funds in TxInputs >= TxOutputs
        tx.getOutputsList().forEach(txOutput -> {
            outputAmount.addAndGet(txOutput.getAmount());
        });
        if(inputAmount.get()<outputAmount.get()){
            System.out.println("Insufficient funds!");
            return false;
        }
        //Remove UTXO if tx is spent if any
        tx.getInputsList().forEach(txInput -> {
            String prevOutputKey = txInput.getPrevTxHash() + ":" + txInput.getPrevOutIndex();
            spentTX.add(prevOutputKey);
            poolUTXO.remove(prevOutputKey);
        });
//        System.out.println("Valid Tx!");
        return true;
    }

    public boolean has(String hash) {
        return txPool.containsKey(hash);
    }

    //clear confirmed Tx in Mempool
    public void clear(List<Transaction> txList) {
        for(Transaction tx : txList){
            String TxID = HashSHA256.hash(tx.toString());
            for(int i=0;i<tx.getOutputsList().size();i++){
                String prevOutputKey = TxID + ":" + i;
                poolUTXO.remove(prevOutputKey);
            }
            txPool.remove(TxID);
        }
    }
}
