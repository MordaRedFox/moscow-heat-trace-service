package ru.moscow.heat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
public class HeatTraceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HeatTraceServiceApplication.class, args);
    }
}
