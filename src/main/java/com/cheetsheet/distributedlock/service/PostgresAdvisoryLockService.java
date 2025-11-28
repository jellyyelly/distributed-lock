package com.cheetsheet.distributedlock.service;

import com.cheetsheet.distributedlock.enums.LockType;
import com.cheetsheet.distributedlock.exception.LockConnectionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * PostgreSQL Advisory Lock을 사용한 분산 락 구현
 * pg_try_advisory_lock 및 pg_advisory_unlock 함수를 활용합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostgresAdvisoryLockService implements DistributedLockService {
    
    @Qualifier("postgresJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public LockType getSupportedType() {
        return LockType.POSTGRES_ADVISORY;
    }
    
    /**
     * 문자열 Lock Key를 정수 해시값으로 변환합니다.
     * PostgreSQL Advisory Lock은 정수 키를 사용하므로 문자열을 해시로 변환합니다.
     * 
     * @param lockKey 문자열 락 키
     * @return 정수 해시값
     */
    public long hashLockKey(String lockKey) {
        return (long) lockKey.hashCode();
    }
    
    /**
     * PostgreSQL pg_try_advisory_lock 함수를 사용하여 논블로킹 방식으로 락을 획득합니다.
     * 
     * @param lockKey 락 식별자
     * @param timeoutSeconds 타임아웃 (초) - Advisory Lock은 타임아웃을 지원하지 않으므로 무시됨
     * @return 락 획득 성공 여부
     */
    @Override
    public boolean acquireLock(String lockKey, int timeoutSeconds) {
        try {
            long hashKey = hashLockKey(lockKey);
            log.debug("Attempting to acquire PostgreSQL advisory lock: key={}, hashKey={}", lockKey, hashKey);
            
            // pg_try_advisory_lock(key) 함수 호출 (논블로킹)
            // 반환값: true = 성공, false = 실패 (이미 다른 세션이 보유 중)
            Boolean result = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(?)",
                Boolean.class,
                hashKey
            );
            
            boolean acquired = result != null && result;
            
            if (acquired) {
                log.debug("Successfully acquired PostgreSQL advisory lock: key={}, hashKey={}", lockKey, hashKey);
            } else {
                log.warn("Failed to acquire PostgreSQL advisory lock (already held): key={}, hashKey={}", 
                        lockKey, hashKey);
            }
            
            return acquired;
            
        } catch (DataAccessException e) {
            log.error("Database connection error while acquiring PostgreSQL lock: key={}", lockKey, e);
            throw new LockConnectionException("PostgreSQL", lockKey, e);
        }
    }
    
    /**
     * PostgreSQL pg_advisory_unlock 함수를 사용하여 락을 해제합니다.
     * 
     * @param lockKey 락 식별자
     * @return 락 해제 성공 여부
     */
    @Override
    public boolean releaseLock(String lockKey) {
        try {
            long hashKey = hashLockKey(lockKey);
            log.debug("Attempting to release PostgreSQL advisory lock: key={}, hashKey={}", lockKey, hashKey);
            
            // pg_advisory_unlock(key) 함수 호출
            // 반환값: true = 성공, false = 락이 존재하지 않거나 다른 세션이 보유 중
            Boolean result = jdbcTemplate.queryForObject(
                "SELECT pg_advisory_unlock(?)",
                Boolean.class,
                hashKey
            );
            
            boolean released = result != null && result;
            
            if (released) {
                log.debug("Successfully released PostgreSQL advisory lock: key={}, hashKey={}", lockKey, hashKey);
            } else {
                log.warn("Attempted to release non-existent or not-owned PostgreSQL lock: key={}, hashKey={}", 
                        lockKey, hashKey);
            }
            
            return released;
            
        } catch (DataAccessException e) {
            log.error("Database connection error while releasing PostgreSQL lock: key={}", lockKey, e);
            throw new LockConnectionException("PostgreSQL", lockKey, e);
        }
    }
}
