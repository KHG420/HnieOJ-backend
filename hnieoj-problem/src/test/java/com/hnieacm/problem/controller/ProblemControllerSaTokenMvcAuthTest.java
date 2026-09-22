package com.hnieacm.problem.controller;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet;
import cn.dev33.satoken.spring.SaTokenContextRegister;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.config.SaTokenMvcAutoConfiguration;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.exception.GlobalExceptionHandler;
import com.hnieacm.common.exception.SaTokenExceptionHandler;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.problem.service.ProblemQueryService;
import com.hnieacm.problem.service.ProblemResourceService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/22
 * @Description: ProblemController 测试数据下载的真实 Spring MVC 鉴权行为回归。
 * <p>MockMvc 基于真实 WebApplicationContext，加载生产 {@link SaTokenMvcAutoConfiguration}
 * 注册的 SaInterceptor 与生产 SaToken 上下文过滤器、生产异常 advice，仅把数据服务与角色来源
 * 替换为桩，让 {@code @SaCheckRole} 注解在真实 DispatcherServlet 流程中生效。若生产配置不再
 * 注册拦截器，匿名/学生请求会直接打到控制器，本测试的 401/403 与「未调用服务」断言将失败。</p>
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ProblemControllerSaTokenMvcAuthTest.MvcAuthTestConfig.class)
class ProblemControllerSaTokenMvcAuthTest {

    private static final Map<String, List<String>> USER_ROLES = Map.of(
            "student-1", List.of(RoleConstant.STUDENT),
            "teacher-1", List.of(RoleConstant.TEACHER),
            "admin-1", List.of(RoleConstant.ADMIN),
            "root-1", List.of(RoleConstant.ROOT));

    private static StpInterface originalStpInterface;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ProblemResourceService problemResourceService;

    private MockMvc mockMvc;
    private String tokenName;

    @BeforeAll
    static void installRoleStub() {
        // 角色数据来源替换为桩；SaManager 原值在 @AfterAll 恢复，避免影响同 JVM 的其他测试
        originalStpInterface = SaManager.getStpInterface();
        SaManager.setStpInterface(new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return List.of();
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return USER_ROLES.getOrDefault(String.valueOf(loginId), List.of());
            }
        });
    }

    @AfterAll
    static void restoreRoleStub() {
        SaManager.setStpInterface(originalStpInterface);
    }

    @BeforeEach
    void setUp() {
        clearInvocations(problemResourceService);
        tokenName = SaManager.getConfig().getTokenName();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(webApplicationContext.getBean(SaTokenContextFilterForJakartaServlet.class))
                .build();
    }

    @Test
    void anonymousFullTestdataDownloadIsRejectedBeforeService() throws Exception {
        mockMvc.perform(get("/api/problems/8/testdata/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED));

        verifyNoInteractions(problemResourceService);
    }

    @Test
    void studentFullTestdataDownloadIsForbiddenBeforeService() throws Exception {
        mockMvc.perform(get("/api/problems/8/testdata/download").header(tokenName, login("student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN));

        verifyNoInteractions(problemResourceService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"teacher-1", "admin-1", "root-1"})
    void privilegedRolesDownloadFullTestdataThroughRealInterceptor(String uid) throws Exception {
        mockMvc.perform(get("/api/problems/8/testdata/download").header(tokenName, login(uid)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"problem-8-testdata.zip\""));

        verify(problemResourceService).writeTestdataZip(eq(8L), any(OutputStream.class));
    }

    @Test
    void anonymousSingleCaseDownloadIsRejectedBeforeService() throws Exception {
        mockMvc.perform(get("/api/problems/8/testdata/3/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED));

        verifyNoInteractions(problemResourceService);
    }

    @Test
    void studentSingleCaseDownloadIsForbiddenBeforeService() throws Exception {
        mockMvc.perform(get("/api/problems/8/testdata/3/download").header(tokenName, login("student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN));

        verifyNoInteractions(problemResourceService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"teacher-1", "admin-1", "root-1"})
    void privilegedRolesDownloadSingleCaseThroughRealInterceptor(String uid) throws Exception {
        mockMvc.perform(get("/api/problems/8/testdata/3/download").header(tokenName, login(uid)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"problem-8-testdata-3.zip\""));

        verify(problemResourceService).writeTestdataCaseZip(eq(8L), eq(3), any(OutputStream.class));
    }

    @Test
    void loginCheckOnRecommendationsIsEnforcedByRealInterceptor() throws Exception {
        mockMvc.perform(get("/api/problems/P1000/recommendations").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED));

        verifyNoInteractions(problemQueryService());
    }

    private ProblemQueryService problemQueryService() {
        return webApplicationContext.getBean(ProblemQueryService.class);
    }

    /** 通过真实请求建立登录会话并取回 token，不使用 mock 上下文。 */
    private String login(String uid) throws Exception {
        String body = mockMvc.perform(post("/test/session/login/{uid}", uid))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(body).path("data").asText();
    }

    @Configuration
    @EnableWebMvc
    @Import({
            SaTokenMvcAutoConfiguration.class,
            SaTokenContextRegister.class,
            SaTokenExceptionHandler.class,
            GlobalExceptionHandler.class
    })
    static class MvcAuthTestConfig {

        @Bean
        ProblemQueryService problemQueryService() {
            return mock(ProblemQueryService.class);
        }

        @Bean
        ProblemResourceService problemResourceService() {
            return mock(ProblemResourceService.class);
        }

        @Bean
        ProblemController problemController(ProblemQueryService problemQueryService,
                                            ProblemResourceService problemResourceService) {
            return new ProblemController(problemQueryService, problemResourceService);
        }

        @Bean
        SessionProbeController sessionProbeController() {
            return new SessionProbeController();
        }
    }

    /** 测试专用登录入口：在真实请求内建立 Sa-Token 会话并把 token 返回给测试。 */
    @RestController
    static class SessionProbeController {

        @PostMapping("/test/session/login/{uid}")
        public Result<String> login(@PathVariable String uid) {
            StpUtil.login(uid);
            return Result.success(StpUtil.getTokenValue());
        }
    }
}
