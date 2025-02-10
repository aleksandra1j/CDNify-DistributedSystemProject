package com.example.originserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class OriginServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OriginServerApplication.class, args);
    }

}
