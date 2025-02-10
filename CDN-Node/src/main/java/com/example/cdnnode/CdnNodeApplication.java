package com.example.cdnnode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;

@SpringBootApplication
@EnableDiscoveryClient
public class CdnNodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CdnNodeApplication.class, args);
    }

    @Bean
    public HttpMessageConverter<byte[]> createByteArrayHttpMessageConverter() {
        return new ByteArrayHttpMessageConverter();
    }
}
