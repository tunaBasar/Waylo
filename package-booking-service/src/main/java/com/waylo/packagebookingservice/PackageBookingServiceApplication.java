package com.waylo.packagebookingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class PackageBookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PackageBookingServiceApplication.class, args);
    }
}
