package com.example.modular_blockchain;

import com.example.modular_blockchain.model.Wallet;
import com.example.modular_blockchain.model.Node;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@SpringBootApplication
public class ModularBlockchainApplication {

    public static void main(String[] args) throws IOException {
        SpringApplication.run(ModularBlockchainApplication.class, args);
        List<Node> nodes = startNetwork();

        //Initiate all nodes to start mining
        for(Node node : nodes){
            new Thread(node::startMining).start();
        }

        //Generate Random Transactions
        new Thread(() -> {
            generateTx(nodes);
        }).start();



    }

    public static void generateTx(List<Node> nodes){
        ArrayList<Wallet> payers = new ArrayList<>();
        ArrayList<Wallet> payees = new ArrayList<>();
        HashSet<Wallet> payerSet = new HashSet<>();

        boolean generatingTx = true;

        for(Node node : nodes){
            payers.add(node.getWallet());
            payees.add(node.getWallet());
        }

        while(generatingTx){

            //randomize a payer
            int payerIdx = (int) (Math.random() * payers.size());
            //randomize a payee
            int payeeIdx = (int) (Math.random() * payees.size());
            //randomize payment amount
            int amount = (int) (Math.random() * payers.get(payerIdx).getBalance())+1;
            //randomize fee from 1-10sats
            int fee = (int) (Math.random() * 10);
            //Create payment every 500ms
            boolean txResponse = payers.get(payerIdx).createTx(payees.get(payeeIdx).getAddress(), amount,fee);
            if(txResponse){
                System.out.println("Wallet:" + payerIdx + "->" + "Wallet:"+ payeeIdx + "---" + "amount:" +amount + "sats" + "---" + "fee:" +fee);
                if(!payerSet.contains(payees.get(payeeIdx))){
                    payers.add(payees.get(payeeIdx));
                }
                payerSet.add(payees.get(payeeIdx));

                //add new wallets every loop
                int nodeIdx = (int) (Math.random() * nodes.size());
                payees.add(new Wallet(nodes.get(nodeIdx).getVersion().getListenAddr()));
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if(!generatingTx){
                break;
            }
        }
    }


    public static List<Node> startNetwork()  throws IOException {
        List<Node> nodes = new ArrayList<>();
        Node node1 = new Node(9090);
        nodes.add(node1);
        for(int i=1;i<4;i++){
            Node node2 = new Node(9090+i);
            nodes.add(node2);
            node1.connect(node2.getVersion().getListenAddr());
            System.out.println("------------------------");
        }
        return nodes;
    }
}
