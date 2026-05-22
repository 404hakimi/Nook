package com.nook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Nook 应用启动入口
 *
 * @author nook
 */
@SpringBootApplication(scanBasePackages = "com.nook")
@EnableScheduling
public class NookServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NookServerApplication.class, args);
    }
}
