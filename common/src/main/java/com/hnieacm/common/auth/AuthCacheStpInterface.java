package com.hnieacm.common.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.common.constant.AuthCacheConstant;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.feign.AuthInternalFeignClient;
import com.hnieacm.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: Sa-Token 角色/权限查询器（MVC 业务服务共用实现）。
 * <p>优先从 Redis 中的网关认证缓存读取；缓存键缺失时通过内部认证 API 刷新一次。</p>
 *
 * <p>放在 common 是因为各 MVC 业务服务需要完全相同的缓存读取语义：任何一处单独实现都会让
 * 各服务的鉴权行为随缓存细节逐渐分叉。</p>
 *
 * <p>项目内另有两处 {@code StpInterface} 实现，职责不同，不共用本类：</p>
 * <ul>
 *   <li>{@code com.hnieacm.auth.auth.StpInterfaceImpl}（hnieoj-user）：它是认证缓存的
 *       <i>生产方</i>，缓存缺失时直接调本进程的 {@code UserAuthCacheService} 重建缓存，
 *       不会回调自己的 Feign 接口。</li>
 *   <li>{@code com.hnieacm.gateway.auth.StpInterfaceImpl}：网关是 WebFlux，只读网关本地缓存，
 *       角色缓存缺失时直接失败，不触发跨服务刷新。</li>
 * </ul>
 *
 * <p>本类不标 {@code @Component}：它在 common 包下，不在各服务 {@code @SpringBootApplication}
 * 的扫描范围里，由 {@code AuthCacheStpInterfaceAutoConfiguration} 注册。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class AuthCacheStpInterface implements StpInterface {

    private static final TypeReference<List<String>> LIST_STRING_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthInternalFeignClient authInternalFeignClient;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        String uid = String.valueOf(loginId);
        // permission 允许为空，但键缺失时仍需触发刷新，否则会把「缓存没建」误判成「没有权限」
        return readOrRefresh(AuthCacheConstant.PERMISSION_CACHE_PREFIX + uid, uid, true);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        String uid = String.valueOf(loginId);
        List<String> roles = readOrRefresh(AuthCacheConstant.ROLE_CACHE_PREFIX + uid, uid, false);
        return roles.isEmpty() ? List.of(RoleConstant.STUDENT) : roles;
    }

    private List<String> readOrRefresh(String key, String uid, boolean allowEmpty) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null && !json.isBlank()) {
            List<String> list = parseJsonList(key, json);
            if (allowEmpty || !list.isEmpty()) {
                return list;
            }
        }

        refreshAuthCache(uid);

        json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        return parseJsonList(key, json);
    }

    private void refreshAuthCache(String uid) {
        try {
            var result = authInternalFeignClient.refreshUserAuthCache(uid);
            if (result == null || result.getCode() != ResultCode.SUCCESS) {
                log.warn("Auth cache refresh failed, uid: {}, result: {}", uid, result);
            }
        } catch (Exception e) {
            log.warn("Auth cache refresh failed, uid: {}", uid, e);
        }
    }

    private List<String> parseJsonList(String key, String json) {
        try {
            List<String> list = objectMapper.readValue(json, LIST_STRING_TYPE);
            return list == null ? Collections.emptyList() : list;
        } catch (Exception e) {
            log.warn("Read auth cache failed, key: {}", key, e);
            return Collections.emptyList();
        }
    }
}
