package com.example.powblockchain.model;

import com.example.powblockchain.Transaction;
import com.example.powblockchain.TxInput;
import com.example.powblockchain.TxOutput;
import com.example.powblockchain.helperFunc.Encoder;
import com.example.powblockchain.helperFunc.HashSHA256;
import lombok.Getter;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.example.powblockchain.helperFunc.Encoder.hexToKey;

@Getter
public class Mempool {
    HashMap<String, Transaction> txPool;
    HashMap<String, Transaction> orphanPool;
    List<String> txIdList;
    HashMap<String, TxOutput> poolUTXO;
    HashMap<String, HashSet<String>> walletMap;
    HashSet<String> spentTX;

    public Mempool() {
        txPool = new HashMap<>();
        poolUTXO = new HashMap<>();
        spentTX = new HashSet<>();
        txIdList = new ArrayList<>();
        walletMap = new HashMap<>();
        orphanPool = new HashMap<>();
    }

    public void addTx(Transaction tx) {
        String TxID = HashSHA256.hashObject(tx);
        txPool.put(TxID,tx);
        txIdList.add(TxID);
        for(int i=0;i<tx.getOutputsList().size();i++){
            String prevOutputKey = TxID + ":" + i;
            poolUTXO.put(prevOutputKey,tx.getOutputsList().get(i));
            walletMap.putIfAbsent(tx.getOutputsList().get(i).getAddress(),new HashSet<>());
            walletMap.get(tx.getOutputsList().get(i).getAddress()).add(TxID);
        }
        for(int i=0;i<tx.getInputsList().size();i++){
            String address = HashSHA256.hash(tx.getInputsList().get(i).getPublicKey());
            walletMap.putIfAbsent(address,new HashSet<>());
            walletMap.get(address).add(TxID);
        }
    }

    public boolean validateTx(Transaction tx,HashMap<String,TxOutput> confirmedUTXO) throws Exception {
        AtomicLong inputAmount = new AtomicLong();
        AtomicLong outputAmount = new AtomicLong();
        if(tx.getInputsCount() < 1 || tx.getOutputsCount() < 1) {
            throw new Exception("Incomplete Tx data!");
        }
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
            // Check if UTXO address matches with new TxInput address
            if(spentTX.contains(prevOutputKey)){
                throw new Exception("Double Spent Transaction!");
            }
            if(confirmedUTXO.containsKey(prevOutputKey) && confirmedUTXO.get(prevOutputKey).getAddress().equals(inputAddress)){
                inputAmount.addAndGet(confirmedUTXO.get(prevOutputKey).getAmount());
            }
            else{
                if(poolUTXO.containsKey(prevOutputKey) && poolUTXO.get(prevOutputKey).getAddress().equals(inputAddress)){
                    inputAmount.addAndGet(poolUTXO.get(prevOutputKey).getAmount());
                }
                else{
                    throw new Exception("UTXO Not Found!");
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
                    throw new Exception("Invalid Signature!");
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
            throw new Exception("Insufficient Funds!");
        }
        //Remove UTXO if tx is spent if any
        tx.getInputsList().forEach(txInput -> {
            String prevOutputKey = txInput.getPrevTxHash() + ":" + txInput.getPrevOutIndex();
            spentTX.add(prevOutputKey);
            poolUTXO.remove(prevOutputKey);
        });
        //System.out.println("Valid Tx!");
        return true;
    }

    public boolean has(String hash) {
        return txPool.containsKey(hash);
    }

    //clear confirmed Tx in Mempool
    public void clear(List<Transaction> txList) {
        for(Transaction tx : txList){
            String TxID = HashSHA256.hashObject(tx);
            for(int i=0;i<tx.getOutputsList().size();i++){
                String prevOutputKey = TxID + ":" + i;
                poolUTXO.remove(prevOutputKey);
                if(walletMap.containsKey(tx.getOutputsList().get(i).getAddress())){
                    walletMap.get(tx.getOutputsList().get(i).getAddress()).remove(TxID);
                }
            }
            for(int i=0;i<tx.getInputsList().size();i++){
                String address = HashSHA256.hash(tx.getInputsList().get(i).getPublicKey());
                if(walletMap.containsKey(address)){
                    walletMap.get(address).remove(TxID);
                }
            }
            txPool.remove(TxID);
            txIdList.remove(TxID);
        }
    }
}
