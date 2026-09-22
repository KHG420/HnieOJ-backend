package com.hnieacm.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnieacm.user.dto.BatchIdsRequest;
import com.hnieacm.user.entity.SysClass;
import com.hnieacm.user.entity.SysCollege;
import com.hnieacm.user.entity.UserInfo;
import com.hnieacm.user.entity.UserProfileChange;
import com.hnieacm.user.mapper.SysClassMapper;
import com.hnieacm.user.mapper.SysCollegeMapper;
import com.hnieacm.user.mapper.UserInfoMapper;
import com.hnieacm.user.mapper.UserProfileChangeMapper;
import com.hnieacm.user.service.ProfileChangeService;
import com.hnieacm.user.service.support.UserAuthStateService;
import com.hnieacm.user.service.support.UserManageValidator;
import com.hnieacm.user.support.MyBatisPlusTestSupport;
import com.hnieacm.user.vo.BatchOperationResultVo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 资料变更审批的事务边界回归。
 *
 * <p>要守住的性质：单条审批（控制器 → {@code approve}）与批量审批（{@code batchApprove}）**都**必须在
 * 事务中执行，且批量路径下**每条申请各自一个事务**——某条失败只回滚该条，已成功的条目保持已提交。</p>
 *
 * <p>为什么必须经代理：{@code @Transactional} 依赖 Spring AOP 代理，同类内部自调用不经过代理，
 * 注解不会生效。因此本用例通过真实的 {@link ProxyFactory} + {@link TransactionInterceptor} +
 * {@link AnnotationTransactionAttributeSource} 取得代理后再调用，事务基础设施是真正生效的；
 * 事务管理器带真实快照/回滚语义（见 {@link SnapshotTransactionManager}），
 * 因此「第二次写入失败时第一笔写入被回滚」是被真的验证，而不是只看有没有抛异常。</p>
 */
class ProfileChangeTransactionBoundaryTest {

    private static final String IDENTITY_ORIGINAL =
            "{\"realname\":\"Old\",\"collegeId\":1,\"grade\":\"2024\",\"classId\":10}";
    private static final String IDENTITY_PROPOSED =
            "{\"realname\":\"New\",\"collegeId\":1,\"grade\":\"2024\",\"classId\":10}";
    private static final String EMAIL_ORIGINAL =
            "{\"realname\":\"Old\",\"collegeId\":1,\"grade\":\"2024\",\"classId\":10,\"email\":\"old@example.com\"}";
    private static final String EMAIL_PROPOSED =
            "{\"realname\":\"Old\",\"collegeId\":1,\"grade\":\"2024\",\"classId\":10,\"email\":\"new@example.com\"}";

    private SnapshotTransactionManager transactionManager;
    private UserProfileChangeMapper userProfileChangeMapper;
    private UserInfoMapper userInfoMapper;
    private UserAuthStateService userAuthStateService;

    private ProfileChangeService service;

    @BeforeAll
    static void initTableInfo() {
        MyBatisPlusTestSupport.initTableInfo(UserInfo.class, UserProfileChange.class);
    }

    @BeforeEach
    void setUp() {
        transactionManager = new SnapshotTransactionManager();
        userProfileChangeMapper = mock(UserProfileChangeMapper.class);
        userInfoMapper = mock(UserInfoMapper.class);
        userAuthStateService = mock(UserAuthStateService.class);

        // MyBatis-Plus 的 BaseMapper.selectOne/updateById 是 default 方法，Mockito 默认执行真实默认实现，
        // 因此这些 stub 必须一次性注册好：重复 when(...) 会在「再次打桩」时反而触发上一次的 answer。
        when(userProfileChangeMapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<UserProfileChange> wrapper = invocation.getArgument(0);
            wrapper.getTargetSql();
            Long wanted = (Long) wrapper.getParamNameValuePairs().values().stream()
                    .filter(Long.class::isInstance).findFirst().orElse(null);
            transactionManager.recordFirstDatabaseAccess();
            return transactionManager.changeRows.get(wanted);
        });
        when(userInfoMapper.selectOne(any())).thenAnswer(invocation -> {
            transactionManager.recordFirstDatabaseAccess();
            return transactionManager.buildUser(invocation.getArgument(0));
        });
        when(userInfoMapper.updateById(any(UserInfo.class))).thenAnswer(invocation -> {
            UserInfo user = invocation.getArgument(0);
            transactionManager.store.put("user:" + user.getUid(), user.getEmail());
            return 1;
        });
        when(userProfileChangeMapper.updateById(any(UserProfileChange.class))).thenAnswer(invocation -> {
            UserProfileChange change = invocation.getArgument(0);
            transactionManager.failIfMarked(change.getId());
            transactionManager.store.put("change:" + change.getId(), change.getStatus());
            return 1;
        });

        SysCollegeMapper sysCollegeMapper = mock(SysCollegeMapper.class);
        SysCollege college = new SysCollege();
        college.setId(1L);
        when(sysCollegeMapper.selectById(1L)).thenReturn(college);
        SysClassMapper sysClassMapper = mock(SysClassMapper.class);
        SysClass sysClass = new SysClass();
        sysClass.setId(10L);
        sysClass.setCollegeId(1L);
        sysClass.setGrade("2024");
        when(sysClassMapper.selectById(10L)).thenReturn(sysClass);
        when(sysClassMapper.selectCount(any())).thenReturn(1L);

        ProfileChangeServiceImpl target = new ProfileChangeServiceImpl(
                userProfileChangeMapper, userInfoMapper, sysCollegeMapper, sysClassMapper,
                userAuthStateService, mock(UserManageValidator.class), new ObjectMapper(),
                transactionManager);

        // 真实 Spring AOP 代理：@Transactional 由 TransactionInterceptor 按注解元数据驱动
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource()));
        service = (ProfileChangeService) factory.getProxy();
    }

    @Test
    void singleApproveRunsInsideTransaction() {
        givenChange(1L, "u1", EMAIL_ORIGINAL, EMAIL_PROPOSED);
        givenUser("u1");

        service.approve(1L, null, "admin");

        assertThat(transactionManager.transactionActiveAtFirstDatabaseAccess)
                .as("单条审批在事务中执行")
                .isTrue();
        assertThat(transactionManager.events).containsExactly("begin", "commit");
    }

    @Test
    void everyBatchItemRunsInItsOwnTransaction() {
        givenChange(1L, "u1", EMAIL_ORIGINAL, EMAIL_PROPOSED);
        givenChange(2L, "u2", EMAIL_ORIGINAL, EMAIL_PROPOSED);
        givenUser("u1");
        givenUser("u2");

        BatchOperationResultVo result = service.batchApprove(ids(1L, 2L), "admin");

        assertThat(transactionManager.transactionActiveAtFirstDatabaseAccess)
                .as("批量路径下每条申请都在事务中执行")
                .isTrue();
        // 每条一个独立事务：begin/commit 交替出现，而不是「一个大事务包住两条」
        assertThat(transactionManager.events).containsExactly("begin", "commit", "begin", "commit");
        assertThat(result.getSuccessCount()).isEqualTo(2);
    }

    @Test
    void failingItemRollsBackItsOwnWritesAndKeepsTheCommittedOne() {
        givenChange(1L, "u1", EMAIL_ORIGINAL, EMAIL_PROPOSED);
        givenChange(2L, "u2", EMAIL_ORIGINAL, EMAIL_PROPOSED);
        givenUser("u1");
        givenUser("u2");
        // 第 2 条：用户行写入成功后，申请行写入失败 —— 该条必须整体回滚
        transactionManager.failChangeUpdateFor(2L, "申请行写入失败");

        BatchOperationResultVo result = service.batchApprove(ids(1L, 2L), "admin");

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getId()).isEqualTo("2");
        // 非业务异常不回传原始 message（可能含 SQL/表名等内部细节）
        assertThat(result.getFailures().get(0).getReason())
                .doesNotContain("申请行写入失败")
                .isEqualTo("审批失败，请稍后重试或查看服务端日志");
        // 第 1 条已提交
        assertThat(transactionManager.store)
                .containsEntry("user:u1", "new@example.com")
                .containsEntry("change:1", "APPROVED");
        // 第 2 条的用户行写入被回滚，申请行也没有落库
        assertThat(transactionManager.store)
                .doesNotContainKey("user:u2")
                .doesNotContainKey("change:2");
        assertThat(transactionManager.events).containsExactly("begin", "commit", "begin", "rollback");
    }

    @Test
    void identityApprovalCacheCleanupIsBoundToItsOwnTransaction() {
        givenChange(1L, "u1", IDENTITY_ORIGINAL, IDENTITY_PROPOSED);
        givenChange(2L, "u2", IDENTITY_ORIGINAL, IDENTITY_PROPOSED);
        givenUser("u1");
        givenUser("u2");
        transactionManager.failChangeUpdateFor(2L, "申请行写入失败");

        List<String> cacheCleared = new ArrayList<>();
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(0);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
            }
            return null;
        }).when(userAuthStateService).afterCommit(any());
        doAnswer(invocation -> {
            cacheCleared.add("cleared");
            return null;
        }).when(userAuthStateService).deleteUserAuthCache(any());

        service.batchApprove(ids(1L, 2L), "admin");

        // 只有提交成功的那条触发缓存清理；被回滚的那条不触发
        assertThat(cacheCleared).hasSize(1);
    }

    // ---------------- 辅助 ----------------

    private BatchIdsRequest ids(Long... values) {
        BatchIdsRequest request = new BatchIdsRequest();
        request.setIds(List.of(values));
        return request;
    }

    private void givenChange(Long id, String uid, String original, String proposed) {
        UserProfileChange change = new UserProfileChange();
        change.setId(id);
        change.setUid(uid);
        change.setStatus("PENDING");
        change.setOriginal(original);
        change.setProposed(proposed);
        transactionManager.changeRows.put(id, change);
    }

    private void givenUser(String uid) {
        transactionManager.knownUids.add(uid);
    }

    /**
     * 带回滚语义的最小事务管理器：begin 时对内存表做快照，rollback 时恢复，commit 时保留。
     * <p>离线环境没有可用的嵌入式数据库，因此用内存表代替真实库；但对「提交/回滚是否真的发生」的判定是真的：
     * 被回滚事务里的写入一定会从 {@link #store} 消失。</p>
     */
    static class SnapshotTransactionManager extends AbstractPlatformTransactionManager {

        final Map<String, String> store = new LinkedHashMap<>();
        final Map<Long, UserProfileChange> changeRows = new LinkedHashMap<>();
        final List<String> knownUids = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        private final Map<Long, String> changeUpdateFailures = new LinkedHashMap<>();
        private final Deque<Map<String, String>> snapshots = new ArrayDeque<>();

        boolean transactionActiveAtFirstDatabaseAccess;
        private boolean firstAccessRecorded;

        void recordFirstDatabaseAccess() {
            if (!firstAccessRecorded) {
                firstAccessRecorded = true;
                transactionActiveAtFirstDatabaseAccess =
                        TransactionSynchronizationManager.isActualTransactionActive();
            }
        }

        void failChangeUpdateFor(Long id, String message) {
            changeUpdateFailures.put(id, message);
        }

        void failIfMarked(Long id) {
            String message = changeUpdateFailures.get(id);
            if (message != null) {
                throw new IllegalStateException(message);
            }
        }

        UserInfo buildUser(LambdaQueryWrapper<UserInfo> wrapper) {
            wrapper.getTargetSql();
            String uid = (String) wrapper.getParamNameValuePairs().values().stream()
                    .filter(String.class::isInstance).findFirst().orElse(null);
            if (uid == null || !knownUids.contains(uid)) {
                return null;
            }
            UserInfo user = new UserInfo();
            user.setUid(uid);
            user.setUsername(uid);
            user.setRealname("Old");
            user.setCollegeId(1L);
            user.setGrade("2024");
            user.setClassId(10L);
            user.setEmail(store.getOrDefault("user:" + uid, "old@example.com"));
            return user;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            snapshots.push(new LinkedHashMap<>(store));
            events.add("begin");
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            snapshots.pop();
            events.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            Map<String, String> snapshot = snapshots.pop();
            store.clear();
            store.putAll(snapshot);
            events.add("rollback");
        }
    }
}
