package com.example.modular_blockchain;

import com.example.modular_blockchain.controller.Network;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class ModularBlockchainApplication {

    public static void main(String[] args) throws IOException {
        SpringApplication.run(ModularBlockchainApplication.class, args);
        Network network = new Network();
        network.start();
    }

}
