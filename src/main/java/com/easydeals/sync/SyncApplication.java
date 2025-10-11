package com.easydeals.sync;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
@MapperScan("com.easydeals.sync.mapper")
public class SyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncApplication.class, args);
    }

}
