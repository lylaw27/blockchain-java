package com.example.powblockchain.controller;


import com.example.powblockchain.Block;
import com.example.powblockchain.Transaction;
import com.example.powblockchain.Version;
import com.example.powblockchain.helperFunc.GrpcStatus;
import com.example.powblockchain.model.Node;

import com.example.powblockchain.model.Wallet;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@CrossOrigin(origins = "*")
@Controller
public class NodeController {

    private final SimpMessagingTemplate template;

    @Autowired
    Node node;

    @Autowired
    public NodeController(SimpMessagingTemplate template) {
        this.template = template;
    }

    @SubscribeMapping("/blocks")
    public void sendInitialResponse() {
        sendTransactions();
        sendBlocks();
        sendPeerList();
        sendWalletList();
    }

    public void sendTransactions() {
        HashMap<String,Transaction> txMap = node.getPool().getTxPool();
        List<String> txIdList = node.getPool().getTxIdList();
        JSONArray responseArray = new JSONArray();
        String jsonString;
        int i = txIdList.size()-1;
        while(i > -1 && responseArray.length()<10) {
            try {
                JSONObject txJson;
                jsonString = JsonFormat.printer().print(txMap.get(txIdList.get(i)));
                txJson = new JSONObject(jsonString);
                responseArray.put(txJson);
            } catch (InvalidProtocolBufferException | JSONException e) {
                throw new RuntimeException(e);
            }
            i--;
        }
        template.convertAndSend("/node/" + node.getVersion().getListenAddr() + "/transactions", responseArray.toString());
    }

    public void sendBlocks() {
        List<Block> blockchain = node.getChain().getBlockList();
        JSONArray responseArray = new JSONArray();
        int i = blockchain.size()-1;
        String headerString;
        while(i > -1 && responseArray.length()<10) {
            try {
                JSONObject responseJson = new JSONObject();
                JSONObject headerJson;
                headerString = JsonFormat.printer().omittingInsignificantWhitespace().print(blockchain.get(i).getHeader());
                headerJson = new JSONObject(headerString);
                responseJson.put("header",headerJson);
                responseJson.put("height",i);
                responseArray.put(responseJson);
            } catch (InvalidProtocolBufferException | JSONException e) {
                throw new RuntimeException(e);
            }
            i--;
        }
        template.convertAndSend("/node/" + node.getVersion().getListenAddr() + "/blocks", responseArray.toString());
    }

    public void sendPeerList() {
        HashMap<String, Version> peers = node.getPeers();
        JSONArray responseArray = new JSONArray();
        peers.forEach((port, version) -> {
            JSONObject peerJson = new JSONObject();
            ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", Integer.parseInt(port)).usePlaintext().build();
            boolean status = GrpcStatus.checkConnection(channel);
            try {
                peerJson.put("node", port);
                peerJson.put("status", Boolean.toString(status));
                peerJson.put("height", version.getHeight());
            } catch (JSONException e) {
                return;
            } finally {
                channel.shutdown();
            }
            responseArray.put(peerJson);
        });
        template.convertAndSend("/node/" + node.getVersion().getListenAddr() + "/peers", responseArray.toString());
    }

    public void sendWalletList() {
        ArrayList<String> wallets = node.getWalletList();
        JSONArray walletArray = new JSONArray();
        int i = wallets.size()-1;
        while(i > -1 && walletArray.length()<10) {
            walletArray.put(wallets.get(i));
            i--;
        }
        template.convertAndSend("/node/" + node.getVersion().getListenAddr() + "/wallets", walletArray.toString());
    }

    @RequestMapping(value = "/node/status", method = RequestMethod.POST)
    @ResponseBody
    public Boolean triggerNodeStatus(){
        if(node.getServer().isShutdown()){
            node.startServer();
            new Thread(node::startMining).start();
            //Connect to one of the saved peer
            for(String peer : node.getVersion().getPeerList()){
                if(node.connect(peer)){
                    break;
                }
            }
        }
        else{
            node.disconnectNode();
        }
        System.out.println(node.getServer().isShutdown());
        return !node.getServer().isShutdown();
    }

    @RequestMapping(value = "/node/block/{blockHeight}", method = RequestMethod.GET)
    @ResponseBody
    public String getBlockInfo(@PathVariable String blockHeight){
        Block block = node.getChain().getBlockByIndex(Integer.parseInt(blockHeight));
        String jsonString;
        try {
             jsonString = JsonFormat.printer().print(block);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        return jsonString;
    }

    @RequestMapping(value = "/node/transaction/{TxID}", method = RequestMethod.GET)
    @ResponseBody
    public String getTxInfo(@PathVariable String TxID){
        if(node.getChain().getTxMap().containsKey(TxID)){
            String TxLocation = node.getChain().getTxMap().get(TxID);
            String blockHash = TxLocation.substring(0,64);
            int TxIndex = Integer.parseInt(TxLocation.substring(65));
            Block block = node.getChain().getBlockMap().get(blockHash);
            Transaction tx = block.getTransactions(TxIndex);
            JSONObject jsonObject;
            try {
                String jsonString = JsonFormat.printer().print(tx);
                jsonObject = new JSONObject(jsonString);
                jsonObject.put("block",node.getChain().getBlockHeight(blockHash));
            } catch (InvalidProtocolBufferException | JSONException e) {
                throw new RuntimeException(e);
            }
            return jsonObject.toString();
        }
        else if(node.getPool().getTxPool().containsKey(TxID)){
            Transaction tx = node.getPool().getTxPool().get(TxID);
            JSONObject jsonObject;
            try {
                String jsonString = JsonFormat.printer().print(tx);
                jsonObject = new JSONObject(jsonString);
                jsonObject.put("block",-1);
            } catch (InvalidProtocolBufferException | JSONException e) {
                throw new RuntimeException(e);
            }
            return jsonObject.toString();
        }
        return null;
    }

    @RequestMapping(value = "/wallet/{address}", method = RequestMethod.GET)
    @ResponseBody
    public String getWalletInfo(@PathVariable String address) {
            if (node.getChain().getWalletMap().containsKey(address)) {
                Wallet wallet = node.getWalletMap().get(address);
                HashMap<String, String> txMap = node.getChain().getTxMap();
                long balance = wallet.getBalance();
                HashSet<String> confirmedSet = node.getChain().getWalletMap().get(address);
                HashSet<String> memPoolSet = node.getPool().getWalletMap().get(address);

                JSONObject walletJson = new JSONObject();

                //Sort Transactions according to time that is confirmed by block
                PriorityQueue<String> txList = new PriorityQueue<>((a, b) -> {
                    String blockHashA = txMap.get(a).substring(0, 64);
                    int txIndexA = Integer.parseInt(txMap.get(a).substring(65));
                    String blockHashB = txMap.get(b).substring(0, 64);
                    int txIndexB = Integer.parseInt(txMap.get(b).substring(65));
                    if (a.equals(b)) {
                        return 0;
                    }
                    //If both Transactions are in the same block then compare by TxIndex
                    if (blockHashA.equals(blockHashB)) {
                        if (txIndexB > txIndexA) {
                            return 1;
                        }
                        return -1;
                    } else if (node.getChain().getBlockHeight(blockHashB) > node.getChain().getBlockHeight(blockHashA)) {
                        return 1;
                    }
                    return -1;
                });
                txList.addAll(confirmedSet);

                try {
                    walletJson.put("address", address);
                    walletJson.put("balance", balance);
                    walletJson.put("node", node.getVersion().getListenAddr());
                    JSONArray txArray = new JSONArray();
                    if(memPoolSet != null){
                        for(String tx : memPoolSet){
                            txArray.put(tx);
                        }
                    }
                    if (!txList.isEmpty()) {
                        for(String tx : txList){
                            txArray.put(tx);
                        }
                    }
                    walletJson.put("txList", txArray);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                return walletJson.toString();
            }
        return null;
    }
}


