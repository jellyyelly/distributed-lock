package com.cheetsheet.distributedlock.service;

import com.cheetsheet.distributedlock.enums.LockType;

/**
 * 모든 락 구현이 따르는 공통 인터페이스
 */
public interface DistributedLockService {
    
    /**
     * 락을 획득합니다.
     * @param lockKey 락 식별자
     * @param timeoutSeconds 타임아웃 (초)
     * @return 락 획득 성공 여부
     */
    boolean acquireLock(String lockKey, int timeoutSeconds);
    
    /**
     * 락을 해제합니다.
     * @param lockKey 락 식별자
     * @return 락 해제 성공 여부
     */
    boolean releaseLock(String lockKey);
    
    /**
     * 이 서비스가 지원하는 락 타입을 반환합니다.
     * @return 지원하는 락 타입
     */
    LockType getSupportedType();
}
