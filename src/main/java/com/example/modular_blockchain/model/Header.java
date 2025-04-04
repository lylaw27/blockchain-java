package com.example.modular_blockchain.model;

import java.sql.Timestamp;

public class Header{
    String rootHash;
    String prevHash;
    int nonce;
    int height;
    Timestamp timestamp;
    public Header(String rootHash, String prevHash, int nonce, int height) {
        this.rootHash = rootHash;
        this.prevHash = prevHash;
        this.nonce = nonce;
        this.height = height;
        timestamp = new Timestamp(System.currentTimeMillis());
    }}
