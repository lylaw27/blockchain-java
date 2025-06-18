package com.example.powblockchain.helperFunc;

import com.example.powblockchain.Transaction;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static com.example.powblockchain.helperFunc.HashSHA256.combineHash;

public class MerkleTree {

    public static String GenerateRoot(List<Transaction> txx ) {
        Deque<String> hashQueue = new ArrayDeque<>();
        txx.forEach((tx)->{
            hashQueue.addLast(HashSHA256.hashObject(tx));
        });
        return CombineLeaf(hashQueue);
    }

    public static String CombineLeaf(Deque<String> hashQueue) {
        //return if one node is left
        if(hashQueue.size() == 1){
            return hashQueue.getFirst();
        }
        //add a copy of the last node to the queue if size is odd number
        if(hashQueue.size()% 2 == 1){
            hashQueue.addLast(hashQueue.peekLast());
        }
        int queueSize = hashQueue.size();
        //combine hashes and add to the queue
        for(int i = 0; i < queueSize/2; i++){
            String firstHash = hashQueue.removeFirst();
            String secondHash = hashQueue.removeFirst();
            hashQueue.addLast(combineHash(firstHash, secondHash));
        }
        //recurse until one hash is left
        return CombineLeaf(hashQueue);
    }
}
