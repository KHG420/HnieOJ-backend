package com.hnieacm.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 为各 MVC 业务服务统一注册 SaInterceptor，使
 * {@code @SaCheckLogin} / {@code @SaCheckRole} / {@code @SaCheckPermission} 注解生效。
 * <p>仅做注解鉴权；{@code /internal/**} 由 InternalApiInterceptor 保护，在此排除。</p>
 */
@AutoConfiguration
@ConditionalOnClass(SaInterceptor.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SaTokenMvcAutoConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/internal/**");
    }
}
