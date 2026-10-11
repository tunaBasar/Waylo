package com.waylo.packagebookingsagaorchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class PackageBookingSagaOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PackageBookingSagaOrchestratorApplication.class, args);
    }
}
