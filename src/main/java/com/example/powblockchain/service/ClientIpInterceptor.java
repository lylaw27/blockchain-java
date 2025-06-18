//package com.example.powblockchain.service;
//
//import io.grpc.Context;
//import io.grpc.Contexts;
//import io.grpc.Grpc;
//import io.grpc.Metadata;
//import io.grpc.ServerCall;
//import io.grpc.ServerCallHandler;
//import io.grpc.ServerInterceptor;
//import java.net.InetSocketAddress;
//
//public class ClientIpInterceptor implements ServerInterceptor {
//
//    public static final Context.Key<String> CLIENT_IP_KEY = Context.key("client-ip");
//
//    @Override
//    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
//            ServerCall<ReqT, RespT> call,
//            Metadata headers,
//            ServerCallHandler<ReqT, RespT> next) {
//
//        InetSocketAddress remoteAddress = (InetSocketAddress) call.getAttributes()
//                .get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
//
//        String clientIp = null;
//        if (remoteAddress != null) {
//            clientIp = remoteAddress.getAddress().getHostAddress();
//        }
//
//        Context context = Context.current().withValue(CLIENT_IP_KEY, clientIp);
//        return Contexts.interceptCall(context, call, headers, next);
//    }
//}
