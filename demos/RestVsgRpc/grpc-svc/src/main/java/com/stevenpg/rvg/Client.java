package com.stevenpg.rvg;

import com.github.javafaker.Faker;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class Client {

    public void executeClient() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("ec2-23-20-142-115.compute-1.amazonaws.com", 8080)
                .usePlaintext()
                .build();

        MessageServiceGrpc.MessageServiceBlockingStub stub = MessageServiceGrpc.newBlockingStub(channel);
        var faker = new Faker();

        // Iterate through for test
        var iterations = 5000;
        long start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i ++) {
            var result = stub.send(Message.newBuilder()
                            .setCar(Message.Car.newBuilder()
                                    .setMake(faker.company().name())
                                    .setModel(faker.esports().team())
                                    .setTrim(faker.commerce().productName())
                                    .setColor(faker.color().name())
                                    .setYear(faker.number().numberBetween(1990, 2021))
                                    .setMileage(faker.number().numberBetween(10_000, 200_000))
                                    .build())
                            .setDriver(Message.Driver.newBuilder()
                                    .setFirstname(faker.name().firstName())
                                    .setLastname(faker.name().lastName())
                                    .setDriverId(faker.idNumber().valid())
                                    .build())
                    .build());
            System.out.println("Result Status: " + result.getStatus());
        }

        long end = System.currentTimeMillis();
        var time = end - start;
        System.out.println("Total Processing time for " + iterations + " grpc messages was " + time + "ms");
    }

}
