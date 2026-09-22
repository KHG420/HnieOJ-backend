package com.hnieacm.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.result.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 鉴权异常响应契约回归。
 * <p>{@link GlobalExceptionHandler} 的 {@code Exception.class} 兜底会匹配任意异常，两个 advice 都未指定
 * 顺序时按 Bean 名排序，兜底会抢在 {@link SaTokenExceptionHandler} 之前把鉴权拒绝转成业务码 500。
 * 本测试用真实 Spring MVC 注册两个 advice，锁定「专用处理器优先」的顺序契约。</p>
 */
class AuthExceptionAdviceOrderTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @RestController
    static class ProbeController {

        @GetMapping("/probe/not-login")
        public String notLogin() {
            throw NotLoginException.newInstance("login", NotLoginException.NOT_TOKEN,
                    NotLoginException.NOT_TOKEN_MESSAGE, null);
        }

        @GetMapping("/probe/not-role")
        public String notRole() {
            throw new NotRoleException("ADMIN");
        }

        @GetMapping("/probe/not-permission")
        public String notPermission() {
            throw new NotPermissionException("problem:update");
        }

        @GetMapping("/probe/other")
        public String other() {
            throw new IllegalStateException("boom");
        }
    }

    @BeforeEach
    void setUp() {
        // 故意按 Bean 名字母序注册：GlobalExceptionHandler 在前，复现未指定顺序时的真实场景
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler(), new SaTokenExceptionHandler())
                .build();
    }

    @Test
    void notLoginMapsToUnauthorized() throws Exception {
        assertThat(businessCode("/probe/not-login")).isEqualTo(ResultCode.UNAUTHORIZED);
    }

    @Test
    void notRoleMapsToForbidden() throws Exception {
        assertThat(businessCode("/probe/not-role")).isEqualTo(ResultCode.FORBIDDEN);
    }

    @Test
    void notPermissionMapsToForbidden() throws Exception {
        assertThat(businessCode("/probe/not-permission")).isEqualTo(ResultCode.FORBIDDEN);
    }

    @Test
    void otherExceptionStillFallsBackToInternalError() throws Exception {
        assertThat(businessCode("/probe/other")).isEqualTo(ResultCode.INTERNAL_ERROR);
    }

    private int businessCode(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("code").asInt();
    }
}
