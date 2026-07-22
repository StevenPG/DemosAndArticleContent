package com.stevenpg.scf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The RSocket surface. Starts an RSocket server (TCP, see application.yml) whose
 * routes are handled by {@link FunctionRSocketController}, which forwards each
 * call to the shared {@code FunctionCatalog}.
 */
@SpringBootApplication
public class RSocketApplication {

    public static void main(String[] args) {
        SpringApplication.run(RSocketApplication.class, args);
    }
}
