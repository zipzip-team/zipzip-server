package org.zipzip.zipzipserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ZipzipServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZipzipServerApplication.class, args);
    }
}
