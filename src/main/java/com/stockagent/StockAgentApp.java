package com.stockagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.stockagent.mapper")
public class StockAgentApp {
    public static void main(String[] args) {
        SpringApplication.run(StockAgentApp.class, args);
        System.out.println();
        System.out.println("==========================================");
        System.out.println("     Stock Agent 启动成功!");
        System.out.println("==========================================");
        System.out.println("  前端页面: http://localhost:9090");
        System.out.println("  API文档:  http://localhost:9090/doc.html");
        System.out.println("  AI对话:   POST http://localhost:9090/api/chat");
        System.out.println("==========================================");
        System.out.println();
    }
}
