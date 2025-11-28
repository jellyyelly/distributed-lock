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
 * MySQL 세션 락을 사용한 분산 락 구현
 * GET_LOCK 및 RELEASE_LOCK 함수를 활용합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MysqlSessionLockService implements DistributedLockService {
    
    @Qualifier("mysqlJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public LockType getSupportedType() {
        return LockType.MYSQL_SESSION;
    }
    
    /**
     * MySQL GET_LOCK 함수를 사용하여 락을 획득합니다.
     * 
     * @param lockKey 락 식별자
     * @param timeoutSeconds 타임아웃 (초)
     * @return 락 획득 성공 여부
     */
    @Override
    public boolean acquireLock(String lockKey, int timeoutSeconds) {
        try {
            log.debug("Attempting to acquire MySQL session lock: key={}, timeout={}s", lockKey, timeoutSeconds);
            
            // GET_LOCK(str, timeout) 함수 호출
            // 반환값: 1 = 성공, 0 = 타임아웃, NULL = 에러
            Integer result = jdbcTemplate.queryForObject(
                "SELECT GET_LOCK(?, ?)",
                Integer.class,
                lockKey,
                timeoutSeconds
            );
            
            boolean acquired = result != null && result == 1;
            
            if (acquired) {
                log.debug("Successfully acquired MySQL session lock: key={}", lockKey);
            } else if (result != null && result == 0) {
                log.warn("Failed to acquire MySQL session lock due to timeout: key={}, timeout={}s", 
                        lockKey, timeoutSeconds);
            } else {
                log.error("MySQL GET_LOCK returned NULL for key={}", lockKey);
            }
            
            return acquired;
            
        } catch (DataAccessException e) {
            log.error("Database connection error while acquiring MySQL lock: key={}", lockKey, e);
            throw new LockConnectionException("MySQL", lockKey, e);
        }
    }
    
    /**
     * MySQL RELEASE_LOCK 함수를 사용하여 락을 해제합니다.
     * 
     * @param lockKey 락 식별자
     * @return 락 해제 성공 여부
     */
    @Override
    public boolean releaseLock(String lockKey) {
        try {
            log.debug("Attempting to release MySQL session lock: key={}", lockKey);
            
            // RELEASE_LOCK(str) 함수 호출
            // 반환값: 1 = 성공, 0 = 락이 존재하지 않음, NULL = 에러
            Integer result = jdbcTemplate.queryForObject(
                "SELECT RELEASE_LOCK(?)",
                Integer.class,
                lockKey
            );
            
            boolean released = result != null && result == 1;
            
            if (released) {
                log.debug("Successfully released MySQL session lock: key={}", lockKey);
            } else if (result != null && result == 0) {
                log.warn("Attempted to release non-existent MySQL lock: key={}", lockKey);
            } else {
                log.error("MySQL RELEASE_LOCK returned NULL for key={}", lockKey);
            }
            
            return released;
            
        } catch (DataAccessException e) {
            log.error("Database connection error while releasing MySQL lock: key={}", lockKey, e);
            throw new LockConnectionException("MySQL", lockKey, e);
        }
    }
}
