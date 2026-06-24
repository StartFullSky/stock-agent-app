package com.stockagent;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@SpringBootApplication
@MapperScan("com.stockagent.mapper")
public class StockAgentApp {

    public static void main(String[] args) {
        // 加载 .env 文件
        loadEnv();

        SpringApplication.run(StockAgentApp.class, args);
        log.info("==========================================");
        log.info("     Stock Agent 启动成功!");
        log.info("==========================================");
        log.info("  前端页面: http://localhost:{}", System.getProperty("server.port", "9090"));
        log.info("  API文档:  http://localhost:{}/doc.html", System.getProperty("server.port", "9090"));
        log.info("==========================================");
    }

    private static void loadEnv() {
        try {
            Path envPath = Path.of(".env");
            if (!Files.exists(envPath)) {
                // 尝试从classpath上级目录找
                envPath = Path.of(System.getProperty("user.dir"), ".env");
            }
            if (!Files.exists(envPath)) return;

            int count = 0;
            try (BufferedReader reader = new BufferedReader(new FileReader(envPath.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        String key = line.substring(0, eq).trim();
                        String value = line.substring(eq + 1).trim();
                        // 移除值两端的引号（单引号或双引号）
                        if ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (System.getenv(key) == null) {
                            System.setProperty(key, value);
                            count++;
                        }
                    }
                }
            }
            // 仅记录加载数量，不输出具体key/value，防止敏感信息泄露到日志
            log.info("[ENV] 已加载 .env 文件（{} 个变量）", count);
        } catch (Exception e) {
            log.warn("[ENV] .env 文件加载失败: {}", e.getMessage());
        }
    }
}
