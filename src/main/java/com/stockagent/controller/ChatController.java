package com.stockagent.controller;

import com.stockagent.common.ApiResponse;
import com.stockagent.entity.ChatHistory;
import com.stockagent.mapper.ChatHistoryMapper;
import com.stockagent.service.AgentService;
import dev.langchain4j.service.TokenStream;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import jakarta.annotation.PreDestroy;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Tag(name = "AI对话")
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatController.class);
    /** 默认用户ID，当请求头未提供X-User-Id时使用 */
    private static final long DEFAULT_USER_ID = 0L;
    private final AgentService agentService;
    private final ChatHistoryMapper chatHistoryMapper;

    // 注：原ExecutorService已移除，TokenStream回调由LangChain4j内部线程池管理

    public ChatController(AgentService agentService, ChatHistoryMapper chatHistoryMapper) {
        this.agentService = agentService;
        this.chatHistoryMapper = chatHistoryMapper;
    }

    @PreDestroy
    public void destroy() {
        log.info("ChatController 销毁");
    }

    @Operation(summary = "发送消息（同步）")
    @PostMapping
    public ApiResponse<String> chat(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                    @RequestBody Map<String, String> request) {
        long uid = userId != null ? userId : DEFAULT_USER_ID;
        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return ApiResponse.fail(400, "消息不能为空");
        }
        log.info("收到消息: {}", message);
        saveChat(uid, "user", message);
        String reply = agentService.chat(uid, message);
        saveChat(uid, "assistant", reply);
        return ApiResponse.ok(reply);
    }

    /**
     * 真流式对话（SSE）
     * 使用 LangChain4j TokenStream 实现 token 级实时推送。
     * 当 AI 需要调用工具（行情查询、搜索等）时，连接保持，工具执行完毕后继续流式输出最终回复。
     */
    @Operation(summary = "流式对话（SSE真流式）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                 @RequestBody Map<String, String> request) {
        long uid = userId != null ? userId : DEFAULT_USER_ID;
        SseEmitter emitter = new SseEmitter(180000L);
        String message = request.get("message");

        if (message == null || message.trim().isEmpty()) {
            try { emitter.send(SseEmitter.event().name("error").data("消息不能为空")); }
            catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }

        log.info("流式消息: {}", message);
        saveChat(uid, "user", message);

        // 累积完整回复，用于保存聊天记录
        // 使用StringBuffer保证线程安全（onNext可能从不同线程调用）
        StringBuffer fullReply = new StringBuffer();

        TokenStream tokenStream = agentService.chatStream(uid, message);

        tokenStream
            .onNext(token -> {
                try {
                    if (token != null) {
                        fullReply.append(token);
                        emitter.send(SseEmitter.event().name("message").data(token));
                    }
                } catch (Exception e) {
                    log.warn("发送SSE消息失败: {}", e.getMessage());
                }
            })
            .onComplete(response -> {
                try {
                    String aiText = (response.content() != null) ? response.content().text() : null;
                    String replyToSave = (aiText != null && !aiText.isEmpty()) ? aiText : fullReply.toString();
                    saveChat(uid, "assistant", replyToSave);
                    emitter.send(SseEmitter.event().name("done").data(""));
                    emitter.complete();
                } catch (Exception e) {
                    log.error("完成SSE流失败", e);
                    emitter.complete();
                }
            })
            .onError(error -> {
                log.error("流式对话异常", error);
                try {
                    emitter.send(SseEmitter.event().name("error").data("对话失败: " + error.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(error);
            })
            .start();

        emitter.onTimeout(() -> {
            log.warn("SSE流超时");
            emitter.complete();
        });
        emitter.onError(t -> log.warn("SSE连接错误: {}", t.getMessage()));

        return emitter;
    }

    @Operation(summary = "测试对话（GET）")
    @GetMapping("/test")
    public ApiResponse<String> test(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                    @RequestParam(defaultValue = "你好") String msg) {
        long uid = userId != null ? userId : DEFAULT_USER_ID;
        return ApiResponse.ok(agentService.chat(uid, msg));
    }

    @Operation(summary = "获取历史对话")
    @GetMapping("/history")
    public ApiResponse<List<ChatHistory>> history(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                   @RequestParam(defaultValue = "50") int limit) {
        long uid = userId != null ? userId : DEFAULT_USER_ID;
        // 校验limit参数，防止负值；Math.min已限制上限200
        int safeLimit = Math.max(1, Math.min(limit, 200));
        Page<ChatHistory> page = new Page<>(1, safeLimit);
        Page<ChatHistory> result = chatHistoryMapper.selectPage(page,
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatHistory>()
                .eq(ChatHistory::getUserId, uid)
                .orderByDesc(ChatHistory::getCreatedAt)
        );
        List<ChatHistory> list = result.getRecords();
        Collections.reverse(list);
        return ApiResponse.ok(list);
    }

    @Operation(summary = "清空历史对话")
    @DeleteMapping("/history")
    public ApiResponse<String> clearHistory(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        long uid = userId != null ? userId : DEFAULT_USER_ID;
        chatHistoryMapper.delete(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatHistory>()
                .eq(ChatHistory::getUserId, uid)
        );
        return ApiResponse.ok("已清空");
    }

    private void saveChat(Long userId, String role, String content) {
        try {
            ChatHistory msg = new ChatHistory();
            msg.setUserId(userId);
            msg.setSessionId("default");
            msg.setRole(role);
            msg.setContent(content);
            msg.setCreatedAt(LocalDateTime.now());
            chatHistoryMapper.insert(msg);
        } catch (Exception e) { log.warn("保存对话失败", e); }
    }
}