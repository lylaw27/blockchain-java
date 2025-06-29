package com.example.powblockchain.model;

import com.example.powblockchain.*;
import com.example.powblockchain.Void;
import com.example.powblockchain.helperFunc.HashSHA256;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import lombok.Getter;

import java.security.*;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

import static com.example.powblockchain.helperFunc.Encoder.bytesToHex;

public class Wallet {

    @Getter
    PublicKey publicKey;
    PrivateKey privateKey;

    @Getter
    String serverIp;
    LinkedList<UTXO> UTXOs = new LinkedList<>();

    @Getter
    String address;

    public Wallet() {
        try{
            KeyPairGenerator ecdsaGenerator = KeyPairGenerator.getInstance("EC");
            ecdsaGenerator.initialize(256, new SecureRandom());
            KeyPair keyPair = ecdsaGenerator.generateKeyPair();
            publicKey = keyPair.getPublic();
            privateKey = keyPair.getPrivate();
            address = HashSHA256.hash(getPublicKeyString());
        }
        catch (NoSuchAlgorithmException e){
            throw new RuntimeException(e);
        }
    }



    public void connect(String serverIp){
        this.serverIp = serverIp;
        ManagedChannel channel = ManagedChannelBuilder.forAddress(serverIp.split(":")[0], Integer.parseInt(serverIp.split(":")[1])).usePlaintext().build();
        NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
        try{
            Void unused = stub.handleWallet(WalletInfo.newBuilder().setAddress(address).build());
        }
        catch (StatusRuntimeException e){
            System.out.println(e.getMessage());
        }
        finally {
            channel.shutdown();
        }
    }

    public boolean createTx(String payee,int sendAmount,int fee){
        int reqAmount = fee + sendAmount;
        if(getBalance()<reqAmount || UTXOs.isEmpty()){
            return false;
        }
        ManagedChannel channel = ManagedChannelBuilder.forAddress(serverIp.split(":")[0], Integer.parseInt(serverIp.split(":")[1])).usePlaintext().build();
        NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
        Transaction.Builder newTx = Transaction.newBuilder();
        long UTXOamount = 0;
        while(UTXOamount<reqAmount) {
            UTXO singleUTXO = UTXOs.removeLast();
            UTXOamount += singleUTXO.getOutput().getAmount();
            int outputIdx = Integer.parseInt(singleUTXO.getID().substring(65));
            String hash = singleUTXO.getID().substring(0, 64);
            TxInput input = TxInput.newBuilder()
                    .setPrevOutIndex(outputIdx)
                    .setPrevTxHash(hash)
                    .setPublicKey(getPublicKeyString())
                    .build();
            newTx.addInputs(input);
        }
            TxOutput payeeOutput = TxOutput.newBuilder()
                .setAmount(sendAmount)
                .setAddress(payee)
                .build();
            TxOutput payerOutput = TxOutput.newBuilder()
                    .setAmount(UTXOamount-reqAmount)
                    .setAddress(address)
                    .build();
            newTx.addOutputs(payeeOutput);
            newTx.addOutputs(payerOutput);
        String TxHash = HashSHA256.hashObject(newTx);
        String sig = createSignature(TxHash);
        Transaction.Builder signedTx = Transaction.newBuilder();
        newTx.getInputsList().forEach(txInput -> {
            signedTx.addInputs(txInput.toBuilder().setSignature(sig).build());
        });
        signedTx.addAllOutputs(newTx.getOutputsList());
        try{
            Void response = stub.handleTransaction(signedTx.build());
        }
        catch (StatusRuntimeException e){
            System.out.println("Connection cannot be established!");
            return false;
        }
        finally {
            channel.shutdown();
        }
        return true;
    }

    public String createSignature(String message) {
        try{
            Signature sign = Signature.getInstance("SHA256withECDSA");
            sign.initSign(privateKey);
            sign.update(message.getBytes());
            byte[] digitalSignature = sign.sign();
            return bytesToHex(digitalSignature);
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    public String getPublicKeyString() {
        return bytesToHex(publicKey.getEncoded());
    }

    public long getBalance(){
        ManagedChannel channel = ManagedChannelBuilder.forAddress(serverIp.split(":")[0], Integer.parseInt(serverIp.split(":")[1])).usePlaintext().build();
        NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);
        try{
            UTXOs = new LinkedList<>(stub.handleBalance(WalletInfo.newBuilder().setAddress(address).build()).getUtxosList());
        }
        catch (StatusRuntimeException e){
            return 0;
        }
        finally {
            channel.shutdown();
        }
        AtomicLong balance = new AtomicLong(0);
        UTXOs.forEach((utxo)->{
            balance.addAndGet(utxo.getOutput().getAmount());
        });
        return balance.get();
    }

}
