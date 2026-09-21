package com.hnieacm.user.support;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 单元测试用的事务管理器占位实现：只记录 begin/commit/rollback 是否被调用，
 * 不做任何真实资源管理（不做快照、不回滚数据）。
 * <p>需要「真的回滚」的用例请用
 * {@code ProfileChangeTransactionBoundaryTest.SnapshotTransactionManager}。</p>
 */
public class NoopTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        // 占位：不管理真实资源
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        // 占位：不管理真实资源
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        // 占位：不管理真实资源
    }
}
