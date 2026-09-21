package com.hnieacm.common.config;

import cn.dev33.satoken.stp.StpInterface;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.auth.AuthCacheStpInterface;
import com.hnieacm.common.feign.AuthInternalFeignClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 为各 MVC 业务服务注册共用的 {@link StpInterface}（{@link AuthCacheStpInterface}）。
 *
 * <p>{@code AuthCacheStpInterface} 在 {@code com.hnieacm.common.auth} 包下，不在任何服务的
 * {@code @SpringBootApplication} 扫描范围内，因此必须靠自动配置注册（仓库既有先例：
 * {@link SaTokenMvcAutoConfiguration}）。</p>
 *
 * <h3>三重条件，缺一不可</h3>
 * <ul>
 *   <li>{@code @ConditionalOnClass}：common 对 openfeign / spring-data-redis 是 optional 依赖，
 *       没有它们的运行环境（如 reactive 网关）不该实例化本配置。</li>
 *   <li>{@code @ConditionalOnWebApplication(SERVLET)}：gateway 是 WebFlux，且它有自己的
 *       {@code StpInterfaceImpl}（语义不同：只读本地缓存、缺失即失败），必须排除。</li>
 *   <li>{@code @ConditionalOnMissingBean(StpInterface.class)}：hnieoj-user 自带
 *       {@code com.hnieacm.auth.auth.StpInterfaceImpl}（认证缓存的生产方），必须让自带的优先。
 *       自动配置在应用自身 Bean 注册完成之后才处理，因此这里能可靠地看到它并退让。</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass({StpInterface.class, FeignClient.class, StringRedisTemplate.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnMissingBean(StpInterface.class)
public class AuthCacheStpInterfaceAutoConfiguration {

    @Bean
    public StpInterface authCacheStpInterface(StringRedisTemplate stringRedisTemplate,
                                              ObjectMapper objectMapper,
                                              AuthInternalFeignClient authInternalFeignClient) {
        return new AuthCacheStpInterface(stringRedisTemplate, objectMapper, authInternalFeignClient);
    }
}
