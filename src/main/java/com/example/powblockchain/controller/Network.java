package com.example.powblockchain.controller;

import com.example.powblockchain.model.Node;
import com.example.powblockchain.model.Wallet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;

@Controller
@CrossOrigin(origins = "*")
public class Network {

    Node node;

    volatile boolean generatingTx = false;

    ArrayList<Wallet> payers;

    @Autowired
    public Network(Node node) {
        this.node = node;
        payers = new ArrayList<>();
        payers.add(node.getWallet());
    }

    @RequestMapping(value = "/connect/{ipAddress}", method = RequestMethod.POST)
    @ResponseBody
    public String initiateConnection(@PathVariable String ipAddress){
        String[] ipAndPort = ipAddress.split(":");
        int port;
        if(ipAndPort.length == 2){
            port = Integer.parseInt(ipAndPort[1]);
        }
        else{
            port = 50051;
        }
        try{
            System.out.println("Connecting to " + ipAndPort[0] + ":" + port);
            node.connect(ipAndPort[0],port);
        }
        catch(Exception e){
            return "error";
        }
        return "connected to :" + ipAddress;
    }

    @RequestMapping(value = "/gentx", method = RequestMethod.POST)
    @ResponseBody
    public boolean toggleTxGeneration(){
        if(generatingTx || node.getChain().getHeight() == -1){
            System.out.println(node.getId() + " stopped generating transactions!");
            generatingTx = false;
            return false;
        }
        else{
            System.out.println(node.getId() + " is generating transactions!");
            generatingTx = true;
            new Thread(this::generateTx).start();
            return true;
        }
    }

    @RequestMapping(value = "/gentx", method = RequestMethod.GET)
    @ResponseBody
    public boolean getTxGeneration(){
        return generatingTx;
    }


    //Generate Random Transactions
    synchronized void generateTx(){
        HashSet<String> payeesSet = new HashSet<>();

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
                newWallet.connect(node.getVersion().getIpAddr()+":"+node.getVersion().getGrpcPort());
                payers.add(newWallet);
                payees.add(newWallet.getAddress());
                payeeIdx = payees.size()-1;
            }

            //randomize payment amount
            int amount = (int) (Math.random() * payers.get(payerIdx).getBalance())+1;

            //randomize fee from 1-10sats
            int fee = (int) (Math.random() * 10);

            //Create payment every 500ms
            payers.get(payerIdx).createTx(payees.get(payeeIdx), amount,fee);

//            if(txResponse) {
//                System.out.println("Wallet:" + payerIdx + "->" + "Wallet:" + payeeIdx + "---" + "amount:" + amount + "sats" + "---" + "fee:" + fee);
//            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
