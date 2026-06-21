package com.stockagent.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BusinessException 单元测试
 */
class BusinessExceptionTest {

    @Test
    @DisplayName("单参数构造应默认code为500")
    void shouldDefaultCodeTo500() {
        BusinessException ex = new BusinessException("出错了");
        assertEquals(500, ex.getCode());
        assertEquals("出错了", ex.getMessage());
    }

    @Test
    @DisplayName("双参数构造应使用指定code")
    void shouldUseSpecifiedCode() {
        BusinessException ex = new BusinessException(404, "未找到");
        assertEquals(404, ex.getCode());
        assertEquals("未找到", ex.getMessage());
    }

    @Test
    @DisplayName("三参数构造应包含cause")
    void shouldWrapCause() {
        RuntimeException cause = new RuntimeException("原始错误");
        BusinessException ex = new BusinessException(500, "业务错误", cause);
        assertEquals(500, ex.getCode());
        assertEquals("业务错误", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
}
