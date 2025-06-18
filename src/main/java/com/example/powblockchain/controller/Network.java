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

    public void start() {

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        node.startServer();

        //    Generate Random Transactions
        new Thread(() -> {
            generateTx();
        }).start();

    }

    public void generateTx(){
        ArrayList<Wallet> payers = new ArrayList<>();
        ArrayList<Wallet> payees = new ArrayList<>();
        HashSet<Wallet> payerSet = new HashSet<>();

//        for(Node node : nodes){
            payers.add(node.getWallet());
            payees.add(node.getWallet());
            node.getWalletMap().put(node.getWallet().getAddress(),node.getWallet());
//        }

        while(true){
            //randomize a payer
            int payerIdx = (int) (Math.random() * payers.size());
            //randomize a payee
            int payeeIdx = (int) (Math.random() * payees.size());

            //randomize payment amount
            int amount = (int) (Math.random() * payers.get(payerIdx).getBalance())+1;
            //randomize fee from 1-10sats
            int fee = (int) (Math.random() * 10);
            //Create payment every 500ms
            boolean txResponse;
            txResponse = payers.get(payerIdx).createTx(payees.get(payeeIdx).getAddress(), amount,fee);
            if(txResponse){
//                System.out.println("Wallet:" + payerIdx + "->" + "Wallet:"+ payeeIdx + "---" + "amount:" +amount + "sats" + "---" + "fee:" +fee);
                if(!payerSet.contains(payees.get(payeeIdx))){
                    payers.add(payees.get(payeeIdx));
                }
                payerSet.add(payees.get(payeeIdx));

//                add new wallets every loop
                int luck = (int) (Math.random() * 4);
                if(luck == 0){
//                    int nodeIdx = (int) (Math.random() * nodes.size());
                    Wallet newWallet = new Wallet();
                    newWallet.connect(node.getServerIp());
                    payees.add(newWallet);
                    node.getWalletMap().put(newWallet.getAddress(),newWallet);
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
