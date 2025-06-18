package com.example.powblockchain.helperFunc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;

public class PublicIpResolver {

    public static String getPublicIp() {
        try {
            URI uri = new URI("http://checkip.amazonaws.com"); // Or other services like ifconfig.me, ipv4.icanhazip.com
            URL url = uri.toURL();
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            return in.readLine();
        } catch (Exception e) {
            System.err.println("Error getting public IP: " + e.getMessage());
            return null;
        }
    }

    public static void main(String[] args) {
        String publicIp = getPublicIp();
        if (publicIp != null) {
            System.out.println("Public IP Address: " + publicIp);
        }
    }
}