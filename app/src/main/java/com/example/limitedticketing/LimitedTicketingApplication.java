package com.example.limitedticketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LimitedTicketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(LimitedTicketingApplication.class, args);
    }
}
