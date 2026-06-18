package com.stockagent.controller;

import com.stockagent.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "AI对话")
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatController.class);
    private final AgentService agentService;

    public ChatController(AgentService agentService) {
        this.agentService = agentService;
    }

    @Operation(summary = "发送消息（POST）")
    @PostMapping
    public Map<String, Object> chat(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String message = request.get("message");
            log.info("收到消息: {}", message);
            String reply = agentService.chat(0L, message);
            result.put("code", 200);
            result.put("data", reply);
            result.put("success", true);
        } catch (Exception e) {
            log.error("AI对话失败", e);
            result.put("code", 500);
            result.put("message", "AI对话失败: " + e.getMessage());
            result.put("success", false);
        }
        return result;
    }

    @Operation(summary = "测试对话（GET，免登录）")
    @GetMapping("/test")
    public Map<String, Object> test(@RequestParam(defaultValue = "你好") String msg) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("测试消息: {}", msg);
            String reply = agentService.chat(0L, msg);
            result.put("code", 200);
            result.put("data", reply);
            result.put("success", true);
        } catch (Exception e) {
            log.error("AI对话失败", e);
            result.put("code", 500);
            result.put("message", "AI对话失败: " + e.getMessage());
            result.put("success", false);
        }
        return result;
    }
}
