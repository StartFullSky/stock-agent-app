package com.stockagent.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiResponse 单元测试
 */
class ApiResponseTest {

    @Test
    @DisplayName("ok应创建成功响应")
    void shouldCreateSuccessResponse() {
        ApiResponse<String> response = ApiResponse.ok("test data");

        assertEquals(200, response.getCode());
        assertTrue(response.isSuccess());
        assertEquals("success", response.getMessage());
        assertEquals("test data", response.getData());
    }

    @Test
    @DisplayName("ok带消息应设置自定义消息")
    void shouldCreateSuccessResponseWithMessage() {
        ApiResponse<String> response = ApiResponse.ok("操作成功", "data");

        assertEquals(200, response.getCode());
        assertTrue(response.isSuccess());
        assertEquals("操作成功", response.getMessage());
        assertEquals("data", response.getData());
    }

    @Test
    @DisplayName("fail应创建失败响应")
    void shouldCreateFailResponse() {
        ApiResponse<Object> response = ApiResponse.fail(400, "参数错误");

        assertEquals(400, response.getCode());
        assertFalse(response.isSuccess());
        assertEquals("参数错误", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    @DisplayName("fail单参数应默认500")
    void shouldDefaultTo500ForSingleArgFail() {
        ApiResponse<Object> response = ApiResponse.fail("出错了");

        assertEquals(500, response.getCode());
        assertFalse(response.isSuccess());
    }

    @Test
    @DisplayName("ok应支持null数据")
    void shouldHandleNullData() {
        ApiResponse<Object> response = ApiResponse.ok(null);

        assertEquals(200, response.getCode());
        assertTrue(response.isSuccess());
        assertNull(response.getData());
    }
}
