package com.example.powblockchain;

import com.example.powblockchain.controller.Network;
import com.example.powblockchain.service.SpringContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PowblockchainApplication {

    public static void main(String[] args) {
        SpringApplication.run(PowblockchainApplication.class, args);
        Network network = SpringContext.getBean(Network.class);
        network.start();
    }
}
