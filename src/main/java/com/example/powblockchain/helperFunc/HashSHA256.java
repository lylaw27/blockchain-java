package com.example.powblockchain.helperFunc;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashSHA256 {

    public static String hash(String input){
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(input.getBytes(StandardCharsets.UTF_8));
            return Encoder.bytesToHex(digest.digest()).toLowerCase();
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


    public static <Obj> String hashObject(Obj object){
        try{
            String jsonString = JsonFormat.printer().omittingInsignificantWhitespace().print((MessageOrBuilder) object);
            return hash(jsonString);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
