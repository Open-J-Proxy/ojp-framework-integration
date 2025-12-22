package com.example.narayana;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;

@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SqlInitializationAutoConfiguration.class
})
public class NarayanaApplication {
    public static void main(String[] args) {
        SpringApplication.run(NarayanaApplication.class, args);
    }
}
