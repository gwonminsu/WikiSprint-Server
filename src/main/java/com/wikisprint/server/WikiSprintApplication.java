package com.wikisprint.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class WikiSprintApplication {

    public static void main(String[] args) {
        SpringApplication.run(WikiSprintApplication.class, args);
    }

}
