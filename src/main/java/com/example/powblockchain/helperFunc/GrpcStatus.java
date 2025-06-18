package com.example.powblockchain.helperFunc;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import static io.grpc.ConnectivityState.*;

public class GrpcStatus {
    public static boolean checkConnection(ManagedChannel channel) {
        ConnectivityState state = channel.getState(true);
        while(state == IDLE || state == CONNECTING) {
            state = channel.getState(false);
            if(state == READY){
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }
}
