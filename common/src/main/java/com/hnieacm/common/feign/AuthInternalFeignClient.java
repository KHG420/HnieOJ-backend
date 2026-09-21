package com.hnieacm.common.feign;

import com.hnieacm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 认证服务内部 API（test → hnieoj-user）。
 * <p>原为 problem / contest / training / discussion / announcement / submission 六个服务的逐字拷贝
 * （BE-05.4），除包名与注释外完全相同，已收敛到 common 保留这一份。</p>
 * <p>各业务服务在自己的 {@code @EnableFeignClients(basePackages = ...)} 里加上
 * {@code com.hnieacm.common.feign} 即可复用；{@code contextId} 保持唯一，
 * 避免多服务同名客户端冲突。hnieoj-user 自身是 {@code name} 指向的服务，
 * 不扫描本包（否则会给自己建一个用不到的代理）。</p>
 */
@FeignClient(name = "hnieoj-user", contextId = "authInternalFeignClient")
public interface AuthInternalFeignClient {

    @PostMapping("/internal/auth/cache/refresh/{uid}")
    Result<Void> refreshUserAuthCache(@PathVariable String uid);
}
