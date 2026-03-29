package com.stevenpg.rvg;

import io.grpc.ServerBuilder;

import java.io.IOException;

public class Server {

    public void startServer() throws IOException, InterruptedException {
        io.grpc.Server server = ServerBuilder
                .forPort(8080)
                .addService(new MessageServiceImpl())
                .build();

        var startedServer = server.start();
        System.out.println("Starting gRPC server on port 8080");
        for(var svc : startedServer.getServices()) {
            System.out.println("Bootstrapping services : " + svc.getServiceDescriptor().getName());
        }

        server.awaitTermination();
    }
}
