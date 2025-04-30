package com.example.modular_blockchain.controller;

import com.example.modular_blockchain.model.Config;
import com.example.modular_blockchain.model.Node;
import com.example.modular_blockchain.model.Wallet;
import com.example.modular_blockchain.service.SpringContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


public class Network {

    NodeController nodeController = SpringContext.getBean(NodeController.class);

    List<Node> nodes = new ArrayList<>();

    public void start() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Node node1 = new Node(9090);
        nodes.add(node1);
        nodeController.getNodes().put(9090,node1);

        for(int i = 1; i< Config.NO_OF_NODE.value; i++){
            Node node2 = new Node(9090+i);
            nodes.add(node2);
            nodeController.getNodes().put(9090+i,node2);
            node1.connect(node2.getVersion().getListenAddr());
            System.out.println("------------------------");
        }
        //    Generate Random Transactions
        new Thread(() -> {
            generateTx(nodes);
        }).start();

    }

    public static void generateTx(List<Node> nodes){
        ArrayList<Wallet> payers = new ArrayList<>();
        ArrayList<Wallet> payees = new ArrayList<>();
        HashSet<Wallet> payerSet = new HashSet<>();

        for(Node node : nodes){
            payers.add(node.getWallet());
            payees.add(node.getWallet());
            node.getWalletMap().put(node.getWallet().getAddress(),node.getWallet());
        }

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
                    int nodeIdx = (int) (Math.random() * nodes.size());
                    Wallet newWallet = new Wallet(nodes.get(nodeIdx).getVersion().getListenAddr());
                    payees.add(newWallet);
                    nodes.get(nodeIdx).getWalletMap().put(newWallet.getAddress(),newWallet);
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
