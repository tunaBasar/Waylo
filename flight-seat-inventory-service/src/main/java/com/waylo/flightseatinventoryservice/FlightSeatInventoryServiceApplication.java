package com.waylo.flightseatinventoryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class FlightSeatInventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlightSeatInventoryServiceApplication.class, args);
    }
}
