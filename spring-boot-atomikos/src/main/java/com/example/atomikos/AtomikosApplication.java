package com.example.atomikos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;

@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SqlInitializationAutoConfiguration.class
})
public class AtomikosApplication {
    public static void main(String[] args) {
        SpringApplication.run(AtomikosApplication.class, args);
    }
}
