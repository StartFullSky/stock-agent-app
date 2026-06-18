package com.stockagent.controller;

import com.stockagent.entity.ChatHistory;
import com.stockagent.mapper.ChatHistoryMapper;
import com.stockagent.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Tag(name = "AI对话")
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatController.class);
    private final AgentService agentService;
    private final ChatHistoryMapper chatHistoryMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(AgentService agentService, ChatHistoryMapper chatHistoryMapper) {
        this.agentService = agentService;
        this.chatHistoryMapper = chatHistoryMapper;
    }

    @Operation(summary = "发送消息（同步）")
    @PostMapping
    public Map<String, Object> chat(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String message = request.get("message");
            log.info("收到消息: {}", message);
            saveChat(0L, "user", message);
            String reply = agentService.chat(0L, message);
            saveChat(0L, "assistant", reply);
            result.put("code", 200); result.put("data", reply); result.put("success", true);
        } catch (Exception e) {
            log.error("AI对话失败", e);
            result.put("code", 500); result.put("message", "AI对话失败: " + e.getMessage()); result.put("success", false);
        }
        return result;
    }

    @Operation(summary = "流式对话（SSE）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, String> request) {
        SseEmitter emitter = new SseEmitter(120000L);
        String message = request.get("message");
        log.info("流式消息: {}", message);

        executor.submit(() -> {
            try {
                saveChat(0L, "user", message);
                // 发送开始事件
                emitter.send(SseEmitter.event().name("start").data(""));

                String reply = agentService.chat(0L, message);
                saveChat(0L, "assistant", reply);

                // 模拟流式：逐段发送
                String[] parts = reply.split("(?<=\\n)");
                StringBuilder sent = new StringBuilder();
                for (String part : parts) {
                    sent.append(part);
                    emitter.send(SseEmitter.event().name("message").data(part));
                    Thread.sleep(30);
                }

                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (Exception e) {
                log.error("流式对话失败", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("对话失败: " + e.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> {});
        return emitter;
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
            result.put("code", 200); result.put("data", list); result.put("success", true);
        } catch (Exception e) {
            result.put("code", 500); result.put("message", e.getMessage()); result.put("success", false);
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
            result.put("code", 200); result.put("data", "已清空"); result.put("success", true);
        } catch (Exception e) {
            result.put("code", 500); result.put("message", e.getMessage()); result.put("success", false);
        }
        return result;
    }

    private void saveChat(Long userId, String role, String content) {
        try {
            ChatHistory msg = new ChatHistory();
            msg.setUserId(userId); msg.setRole(role); msg.setContent(content); msg.setCreatedAt(LocalDateTime.now());
            chatHistoryMapper.insert(msg);
        } catch (Exception e) { log.warn("保存对话失败: {}", e.getMessage()); }
    }
}
