package com.nook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Nook 启动入口。
 *
 * 扫描包覆盖全部 biz 模块（com.nook 下所有子包）。
 */
@SpringBootApplication(scanBasePackages = "com.nook")
public class NookApplication {

    public static void main(String[] args) {
        SpringApplication.run(NookApplication.class, args);
    }
}
