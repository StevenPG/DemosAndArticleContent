package com.example;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    CommandLineRunner seedData(PluginRepository repository) {
        return args -> {
            // Only seed if the table is empty — safe to run on every startup.
            if (repository.count() == 0) {
                repository.save(new PluginRegistration("hello",   "com.example.HelloPlugin"));
                repository.save(new PluginRegistration("goodbye", "com.example.GoodbyePlugin"));
                repository.save(new PluginRegistration("wave",    "com.example.WavePlugin"));
            }
        };
    }
}
