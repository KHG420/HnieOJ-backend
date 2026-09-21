package com.hnieacm.common.config;

import cn.dev33.satoken.stp.StpInterface;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.auth.AuthCacheStpInterface;
import com.hnieacm.common.feign.AuthInternalFeignClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 共用 {@link StpInterface} 自动配置的装配边界。
 * <p>这三条分支正是各服务能否继续鉴权的分界：业务服务必须拿到共用实现；
 * hnieoj-user 必须让自带的（认证缓存生产方）优先；gateway（WebFlux）必须完全不加载。</p>
 */
class AuthCacheStpInterfaceAutoConfigurationTest {

    private WebApplicationContextRunner servletRunner() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AuthCacheStpInterfaceAutoConfiguration.class))
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(AuthInternalFeignClient.class, () -> mock(AuthInternalFeignClient.class));
    }

    @Test
    void servletServiceWithoutOwnImplementationGetsSharedOne() {
        servletRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(StpInterface.class);
            assertThat(context.getBean(StpInterface.class)).isInstanceOf(AuthCacheStpInterface.class);
        });
    }

    @Test
    void serviceWithOwnImplementationWins() {
        // hnieoj-user 场景：它自带 com.hnieacm.auth.auth.StpInterfaceImpl（认证缓存的生产方），
        // 必须让自带的优先，否则会回调自己的 Feign 接口重建缓存
        servletRunner()
                .withUserConfiguration(OwnStpInterfaceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(StpInterface.class);
                    assertThat(context.getBean(StpInterface.class)).isSameAs(OWN);
                    assertThat(context.getBean(StpInterface.class)).isNotInstanceOf(AuthCacheStpInterface.class);
                });
    }

    @Test
    void nonServletWebApplicationBacksOff() {
        // gateway 是 WebFlux（非 SERVLET），且有自己的 StpInterfaceImpl，语义不同
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AuthCacheStpInterfaceAutoConfiguration.class))
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(AuthInternalFeignClient.class, () -> mock(AuthInternalFeignClient.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(StpInterface.class);
                });
    }

    @Test
    void backsOffWhenOpenFeignIsAbsent() {
        // common 对 openfeign 是 optional 依赖，缺它的运行环境不该尝试装配
        servletRunner()
                .withClassLoader(new FilteredClassLoader(FeignClient.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(StpInterface.class);
                });
    }

    /**
     * 上面的用例显式传入自动配置类，因此不能证明它被 {@code AutoConfiguration.imports} 收录。
     * 服务实际启动时靠的正是这个文件；漏登记会让所有业务服务悄悄退回 Sa-Token 的默认实现
     * （角色/权限恒为空），表现为管理员被拒绝访问，且编译期毫无提示。
     */
    @Test
    void isRegisteredInAutoConfigurationImports() throws Exception {
        String resource = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("common 必须提供 " + resource).isNotNull();
            String content = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(content.lines().map(String::trim).toList())
                    .contains(AuthCacheStpInterfaceAutoConfiguration.class.getName());
        }
    }

    private static final StpInterface OWN = new StpInterface() {
        @Override
        public java.util.List<String> getPermissionList(Object loginId, String loginType) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<String> getRoleList(Object loginId, String loginType) {
            return java.util.List.of();
        }
    };

    @Configuration(proxyBeanMethods = false)
    static class OwnStpInterfaceConfiguration {
        @Bean
        StpInterface ownStpInterface() {
            return OWN;
        }
    }
}
