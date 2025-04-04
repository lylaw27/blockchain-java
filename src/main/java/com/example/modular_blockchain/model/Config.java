package com.example.modular_blockchain.model;

public enum Config {
    COINBASE_TX_AMOUNT(100000);

    public final long value;

    Config(long value) {
        this.value = value;
    }
}
