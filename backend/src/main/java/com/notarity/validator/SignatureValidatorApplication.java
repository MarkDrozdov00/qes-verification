package com.notarity.validator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SignatureValidatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SignatureValidatorApplication.class, args);
    }
}
