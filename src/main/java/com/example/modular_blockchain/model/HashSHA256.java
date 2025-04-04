package com.example.modular_blockchain.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashSHA256<Obj> {
    public static String hash(String input){
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(input.getBytes(StandardCharsets.UTF_8));
            return Encoder.bytesToHex(digest.digest());
        }
            catch (NoSuchAlgorithmException e){
            throw new RuntimeException(e);
        }
    }

    public static String combineHash(String input1, String input2){
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(input1.getBytes(StandardCharsets.UTF_8));
            digest.update(input2.getBytes(StandardCharsets.UTF_8));
            return Encoder.bytesToHex(digest.digest());
        }
        catch (NoSuchAlgorithmException e){
            throw new RuntimeException(e);
        }
    }


    public static String random() {
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            int num = (int) (Math.random() * 1000000);
            digest.update(Integer.toString(num).getBytes(StandardCharsets.UTF_8));
            return Encoder.bytesToHex(digest.digest());
        }
        catch (NoSuchAlgorithmException e){
            throw new RuntimeException(e);
        }
    }

    public String hashObject(Obj object){
        try{
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            String jsonString = ow.writeValueAsString(object);
            return hash(jsonString);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
