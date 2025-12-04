package com.cheatsheet.distributedlock.service;

import com.cheatsheet.distributedlock.exception.LockAcquisitionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * 락 획득 재시도를 담당하는 서비스
 * @Retryable 애너테이션을 사용하여 선언적 재시도 로직 제공
 */
@Service
@Slf4j
public class LockRetryService {
    
    /**
     * @Retryable을 사용한 재시도 로직을 포함한 락 획득을 시도합니다.
     * 최대 4번 시도 (최초 1회 + 재시도 3회), 재시도 간격 100ms
     * 
     * @param lockService Lock Service
     * @param lockKey 락 키
     * @param timeout 타임아웃 (초)
     * @return 락 획득 성공 여부
     * @throws LockAcquisitionException 락 획득 실패 시 (재시도 트리거용)
     */
    @Retryable(
            retryFor = LockAcquisitionException.class,
            maxAttempts = 4, // 최초 시도 1회 + 재시도 3회
            backoff = @Backoff(delay = 100) // 재시도 간격 100ms
    )
    public boolean acquireLockWithRetry(
            DistributedLockService lockService,
            String lockKey,
            int timeout
    ) {
        log.debug("Attempting to acquire lock: {}", lockKey);
        
        boolean acquired = lockService.acquireLock(lockKey, timeout);
        
        if (!acquired) {
            log.debug("Lock acquisition failed, will retry: {}", lockKey);
            throw new LockAcquisitionException("Lock acquisition failed, retrying: " + lockKey, lockKey);
        }
        
        log.debug("Lock acquired successfully: {}", lockKey);
        return true;
    }
}
