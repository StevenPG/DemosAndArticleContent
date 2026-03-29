package com.stevenpg.rvg.restapi;

import com.github.javafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class ProcessExecutor implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(ProcessExecutor.class);

    @Value("${target:#{null}}")
    private String target;

    @Override
    public void run(String...args) {
        if (target != null) {
            logger.info("Application started with target: " + target + " \n To kill this application, press Ctrl + C.");
            WebClient webClient = WebClient.builder()
                    .baseUrl(target + "/v1/create")
                    .build();

            var faker = new Faker();
            long start = System.currentTimeMillis();

            var iterations = 5000;
            for(int i = 0; i < iterations; i++) {
                var msg = new Message();
                var car = new Car();
                car.setMake(faker.company().name());
                car.setModel(faker.esports().team());
                car.setTrim(faker.commerce().productName());
                car.setColor(faker.color().name());
                car.setYear(faker.number().numberBetween(1990, 2021));
                car.setMileage(faker.number().numberBetween(10_000, 200_000));
                var driver = new Driver();
                driver.setFirstname(faker.name().firstName());
                driver.setLastname(faker.name().lastName());
                driver.setDriverId(faker.idNumber().valid());

                msg.setCar(car);
                msg.setDriver(driver);

                var result = webClient.post()
                        .bodyValue(msg)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                System.out.println("Result Status: " + result);
            }

            long end = System.currentTimeMillis();
            var time = end - start;
            System.out.println("Total Processing time for " + iterations + " grpc messages was " + time + "ms");
        }
    }
}