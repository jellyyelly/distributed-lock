package com.cheatsheet.distributedlock.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.cheatsheet.distributedlock.enums.LockType;
import com.cheatsheet.distributedlock.exception.LockConnectionException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Redis SETNX를 사용한 분산 락 구현
 * SET if Not eXists 명령을 사용하여 간단한 락을 제공합니다.
 */
@Slf4j
@Service
public class RedisSetnxLockService implements DistributedLockService {
    
    /**
     * 기본 만료 시간 (초) - 데드락 방지용
     */
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    
    private final StringRedisTemplate redisTemplate;
    
    /**
     * 스레드별 락 소유자 ID 저장
     * 각 락 키에 대해 어떤 소유자 ID로 획득했는지 추적
     */
    private final Map<String, String> lockOwnerMap = new ConcurrentHashMap<>();
    
    public RedisSetnxLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public LockType getSupportedType() {
        return LockType.REDIS_SETNX;
    }
    
    /**
     * SETNX를 사용하여 락을 획득합니다.
     * SET NX EX 명령을 사용하여 원자적으로 락을 설정하고 만료 시간을 지정합니다.
     * 
     * @param lockKey 락 식별자
     * @param timeoutSeconds 타임아웃 (초) - 0 이하인 경우 기본값 사용
     * @return 락 획득 성공 여부
     */
    @Override
    public boolean acquireLock(String lockKey, int timeoutSeconds) {
        try {
            // 고유 소유자 ID 생성 (UUID 기반)
            String ownerId = UUID.randomUUID().toString();
            
            // 타임아웃이 0 이하인 경우 기본값 사용
            int effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
            
            log.debug("Attempting to acquire Redis SETNX lock: key={}, timeout={}s, ownerId={}", 
                    lockKey, effectiveTimeout, ownerId);
            
            // SET NX EX 명령 실행 (원자적 연산)
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, ownerId, effectiveTimeout, TimeUnit.SECONDS);
            
            boolean acquired = Boolean.TRUE.equals(result);
            
            if (acquired) {
                // 소유자 ID 저장 (해제 시 사용)
                lockOwnerMap.put(lockKey, ownerId);
                log.debug("Successfully acquired Redis SETNX lock: key={}, ownerId={}", lockKey, ownerId);
            } else {
                log.debug("Failed to acquire Redis SETNX lock (already held): key={}", lockKey);
            }
            
            return acquired;
            
        } catch (Exception e) {
            log.error("Error while acquiring Redis SETNX lock: key={}", lockKey, e);
            throw new LockConnectionException("Redis", lockKey, e);
        }
    }
    
    /**
     * DEL 명령을 사용하여 락을 해제합니다.
     * 
     * @param lockKey 락 식별자
     * @return 락 해제 성공 여부
     */
    @Override
    public boolean releaseLock(String lockKey) {
        try {
            // 저장된 소유자 ID 조회
            String ownerId = lockOwnerMap.get(lockKey);
            
            if (ownerId == null) {
                log.warn("Attempted to release lock without owner ID: key={}", lockKey);
                return false;
            }
            
            log.debug("Attempting to release Redis SETNX lock: key={}, ownerId={}", lockKey, ownerId);
            
            // DEL 명령 실행
            Boolean result = redisTemplate.delete(lockKey);
            
            boolean released = Boolean.TRUE.equals(result);
            
            if (released) {
                lockOwnerMap.remove(lockKey);
                log.debug("Successfully released Redis SETNX lock: key={}", lockKey);
            } else {
                log.warn("Failed to release Redis SETNX lock (not exists): key={}", lockKey);
            }
            
            return released;
            
        } catch (Exception e) {
            log.error("Error while releasing Redis SETNX lock: key={}", lockKey, e);
            throw new LockConnectionException("Redis", lockKey, e);
        }
    }
    
    /**
     * 특정 소유자 ID로 락 획득을 시도합니다.
     * 테스트 및 특수 상황에서 사용됩니다.
     * 
     * @param lockKey 락 식별자
     * @param timeoutSeconds 타임아웃 (초)
     * @param ownerId 소유자 ID
     * @return 락 획득 성공 여부
     */
    public boolean acquireLockWithOwner(String lockKey, int timeoutSeconds, String ownerId) {
        try {
            int effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
            
            log.debug("Attempting to acquire Redis SETNX lock with specific owner: key={}, timeout={}s, ownerId={}", 
                    lockKey, effectiveTimeout, ownerId);
            
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, ownerId, effectiveTimeout, TimeUnit.SECONDS);
            
            boolean acquired = Boolean.TRUE.equals(result);
            
            if (acquired) {
                lockOwnerMap.put(lockKey, ownerId);
                log.debug("Successfully acquired Redis SETNX lock: key={}, ownerId={}", lockKey, ownerId);
            } else {
                log.debug("Failed to acquire Redis SETNX lock (already held): key={}", lockKey);
            }
            
            return acquired;
            
        } catch (Exception e) {
            log.error("Error while acquiring Redis SETNX lock: key={}", lockKey, e);
            throw new LockConnectionException("Redis", lockKey, e);
        }
    }
    
    /**
     * 락의 현재 소유자 ID를 반환합니다.
     * 테스트용 메서드입니다.
     * 
     * @param lockKey 락 식별자
     * @return 소유자 ID (없으면 null)
     */
    public String getOwnerIdForKey(String lockKey) {
        return lockOwnerMap.get(lockKey);
    }
}
