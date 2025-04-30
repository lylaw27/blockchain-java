package com.example.modular_blockchain.model;

public enum Config {
    COINBASE_TX_AMOUNT(100000),
    DIFFICULTY(4),
    NO_OF_NODE(20);

    public final int value;

    Config(int value) {
        this.value = value;
    }
}
