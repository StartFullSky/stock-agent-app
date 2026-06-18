package com.stockagent.controller;

import com.stockagent.entity.ChatHistory;
import com.stockagent.mapper.ChatHistoryMapper;
import com.stockagent.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "AI对话")
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatController.class);
    private final AgentService agentService;
    private final ChatHistoryMapper chatHistoryMapper;

    public ChatController(AgentService agentService, ChatHistoryMapper chatHistoryMapper) {
        this.agentService = agentService;
        this.chatHistoryMapper = chatHistoryMapper;
    }

    @Operation(summary = "发送消息")
    @PostMapping
    public Map<String, Object> chat(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String message = request.get("message");
            log.info("收到消息: {}", message);

            // 保存用户消息
            ChatHistory userMsg = new ChatHistory();
            userMsg.setUserId(0L);
            userMsg.setRole("user");
            userMsg.setContent(message);
            userMsg.setCreatedAt(LocalDateTime.now());
            chatHistoryMapper.insert(userMsg);

            // 调用AI
            String reply = agentService.chat(0L, message);

            // 保存AI回复
            ChatHistory aiMsg = new ChatHistory();
            aiMsg.setUserId(0L);
            aiMsg.setRole("assistant");
            aiMsg.setContent(reply);
            aiMsg.setCreatedAt(LocalDateTime.now());
            chatHistoryMapper.insert(aiMsg);

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

    @Operation(summary = "测试对话（GET）")
    @GetMapping("/test")
    public Map<String, Object> test(@RequestParam(defaultValue = "你好") String msg) {
        Map<String, Object> r = new HashMap<>();
        try {
            String reply = agentService.chat(0L, msg);
            r.put("code", 200); r.put("data", reply); r.put("success", true);
        } catch (Exception e) {
            r.put("code", 500); r.put("message", e.getMessage()); r.put("success", false);
        }
        return r;
    }

    @Operation(summary = "获取历史对话")
    @GetMapping("/history")
    public Map<String, Object> history(@RequestParam(defaultValue = "50") int limit) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<ChatHistory> list = chatHistoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatHistory>()
                    .eq(ChatHistory::getUserId, 0L)
                    .orderByDesc(ChatHistory::getCreatedAt)
                    .last("LIMIT " + limit)
            );
            Collections.reverse(list);
            result.put("code", 200);
            result.put("data", list);
            result.put("success", true);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            result.put("success", false);
        }
        return result;
    }

    @Operation(summary = "清空历史对话")
    @DeleteMapping("/history")
    public Map<String, Object> clearHistory() {
        Map<String, Object> result = new HashMap<>();
        try {
            chatHistoryMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatHistory>()
                    .eq(ChatHistory::getUserId, 0L)
            );
            result.put("code", 200);
            result.put("data", "已清空");
            result.put("success", true);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            result.put("success", false);
        }
        return result;
    }
}
