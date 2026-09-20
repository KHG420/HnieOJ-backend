package com.hnieacm.discussion.feign;

import com.hnieacm.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: Auth 服务内部接口
 */
@FeignClient(name = "hnieoj-user", contextId = "authInternalFeignClient")
public interface AuthInternalFeignClient {

    /**
     * 刷新指定用户在网关侧的鉴权缓存（角色与权限），供调用方在用户权限变更后通过 Feign 触发。
     *
     * @param uid 目标用户 uid
     * @return 统一的空结果，表示缓存刷新请求已由 hnieoj-user 处理
     */
    @PostMapping("/internal/auth/cache/refresh/{uid}")
    Result<Void> refreshUserAuthCache(@PathVariable String uid);
}
