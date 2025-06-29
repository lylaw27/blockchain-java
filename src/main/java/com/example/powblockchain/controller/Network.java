package com.example.powblockchain.controller;

import com.example.powblockchain.model.Node;
import com.example.powblockchain.model.Wallet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;

@CrossOrigin(origins = "http://127.0.0.1/")
@Controller
public class Network {

    @Autowired
    Node node;

    volatile boolean generatingTx = false;

    @RequestMapping(value = "/node/{ipAddress}", method = RequestMethod.POST)
    public String initiateConnection(@PathVariable String ipAddress){
        try{
            node.connect(ipAddress);
        }
        catch(Exception e){
            return "error";
        }
        return "connected to :" + ipAddress;
    }

    @RequestMapping(value = "/mine", method = RequestMethod.POST)
    public String initiateMining(){
        node.resetMining();
        return "Started Mining!";
    }

    @RequestMapping(value = "/generateTx", method = RequestMethod.POST)
    public String toggleTxGeneration(){
        generatingTx = true;
        new Thread(this::generateTx).start();
        return "Generating Transactions!";
    }

    public void start() {

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        node.startServer();
        payers.add(node.getWallet());
    }

    ArrayList<Wallet> payers = new ArrayList<>();

    //Generate Random Transactions
    synchronized void generateTx(){
        HashSet<String> payeesSet = new HashSet<>();

//        node.getWalletMap().put(node.getWallet().getAddress(),node.getWallet());

        while(generatingTx){
            payeesSet.addAll(node.getChain().getWalletMap().keySet());
            payeesSet.addAll(node.getPool().getWalletMap().keySet());
            ArrayList<String> payees = new ArrayList<>(payeesSet);

            //randomize a payer
            int payerIdx = (int) (Math.random() * payers.size());

            //randomize a payee
            int payeeIdx = (int) (Math.random() * payees.size());

            int luck = (int) (Math.random() * 4);
            if(luck == 0){
                Wallet newWallet = new Wallet();
                newWallet.connect(node.getServerIp());
                payers.add(newWallet);
                payees.add(newWallet.getAddress());
                payeeIdx = payees.size()-1;
            }

            //randomize payment amount
            int amount = (int) (Math.random() * payers.get(payerIdx).getBalance())+1;
            //randomize fee from 1-10sats
            int fee = (int) (Math.random() * 10);
            //Create payment every 500ms
            boolean txResponse;

            txResponse = payers.get(payerIdx).createTx(payees.get(payeeIdx), amount,fee);

            if(txResponse) {
                System.out.println("Wallet:" + payerIdx + "->" + "Wallet:" + payeeIdx + "---" + "amount:" + amount + "sats" + "---" + "fee:" + fee);
            }
//            else{
//                generateTx();
//            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
