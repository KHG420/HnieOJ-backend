package com.hnieacm.common.feign;

import com.hnieacm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 认证服务的内部接口客户端（各 MVC 业务服务 → hnieoj-user）。
 * <p>由 {@code StpInterface} 实现类在本地认证缓存缺失时调用，用于重建该用户
 * 在 Redis 中的 roles/permissions 缓存。</p>
 * <p>各业务服务在自己的 {@code @EnableFeignClients(basePackages = ...)} 中加入
 * {@code com.hnieacm.common.feign} 即可复用它；{@code contextId} 全局唯一，避免同名客户端冲突。
 * hnieoj-user 是 {@code name} 指向的服务本身，不扫描本包，以免给自己创建不会被调用的代理。</p>
 */
@FeignClient(name = "hnieoj-user", contextId = "authInternalFeignClient")
public interface AuthInternalFeignClient {

    /**
     * 重建指定用户的 roles/permissions 认证缓存。
     *
     * @param uid 用户 uid
     * @return 重建结果；业务码非成功码表示重建失败，调用方按最小权限回退
     */
    @PostMapping("/internal/auth/cache/refresh/{uid}")
    Result<Void> refreshUserAuthCache(@PathVariable String uid);
}
