package com.stockagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@MapperScan("com.stockagent.mapper")
public class StockAgentApp {

    public static void main(String[] args) {
        // 加载 .env 文件
        loadEnv();

        SpringApplication.run(StockAgentApp.class, args);
        System.out.println();
        System.out.println("==========================================");
        System.out.println("     Stock Agent 启动成功!");
        System.out.println("==========================================");
        System.out.println("  前端页面: http://localhost:9090");
        System.out.println("  API文档:  http://localhost:9090/doc.html");
        System.out.println("==========================================");
        System.out.println();
    }

    private static void loadEnv() {
        try {
            Path envPath = Path.of(".env");
            if (!Files.exists(envPath)) {
                // 尝试从classpath上级目录找
                envPath = Path.of(System.getProperty("user.dir"), ".env");
            }
            if (!Files.exists(envPath)) return;

            try (BufferedReader reader = new BufferedReader(new FileReader(envPath.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        String key = line.substring(0, eq).trim();
                        String value = line.substring(eq + 1).trim();
                        if (System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                }
            }
            System.out.println("[ENV] 已加载 .env 文件");
        } catch (Exception e) {
            System.out.println("[ENV] .env 文件加载失败: " + e.getMessage());
        }
    }
}
