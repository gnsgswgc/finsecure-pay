package com.finsecure.pay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FinsecurePayApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinsecurePayApplication.class, args);
    }
}
