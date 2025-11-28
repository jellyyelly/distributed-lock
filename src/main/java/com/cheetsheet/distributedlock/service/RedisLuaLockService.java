package com.cheetsheet.distributedlock.service;

import com.cheetsheet.distributedlock.enums.LockType;
import com.cheetsheet.distributedlock.exception.LockConnectionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis Lua Script를 사용한 분산 락 구현
 * 원자적 연산을 보장하며, 락 소유자 검증을 통해 안전한 락 해제를 지원합니다.
 */
@Slf4j
@Service
public class RedisLuaLockService implements DistributedLockService {
    
    /**
     * 락 획득 Lua Script
     * - 락이 존재하지 않으면 설정하고 만료 시간 지정
     * - 이미 존재하면 실패 반환
     */
    private static final String ACQUIRE_LOCK_SCRIPT = """
        local lockKey = KEYS[1]
        local ownerId = ARGV[1]
        local ttl = ARGV[2]
        
        if redis.call('EXISTS', lockKey) == 0 then
            redis.call('SET', lockKey, ownerId, 'EX', ttl)
            return 1
        else
            return 0
        end
        """;
    
    /**
     * 락 해제 Lua Script
     * - 락 소유자를 검증한 후 삭제
     * - 소유자가 일치하지 않으면 실패 반환
     */
    private static final String RELEASE_LOCK_SCRIPT = """
        local lockKey = KEYS[1]
        local ownerId = ARGV[1]
        
        if redis.call('GET', lockKey) == ownerId then
            return redis.call('DEL', lockKey)
        else
            return 0
        end
        """;

    /**
     * 기본 만료 시간 (초) - 데드락 방지용
     */
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    
    private final StringRedisTemplate redisTemplate;
    private RedisScript<Long> acquireLockScript;
    private RedisScript<Long> releaseLockScript;
    
    /**
     * 스레드별 락 소유자 ID 저장
     * 각 락 키에 대해 어떤 소유자 ID로 획득했는지 추적
     */
    private final Map<String, String> lockOwnerMap = new ConcurrentHashMap<>();
    
    public RedisLuaLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @PostConstruct
    public void init() {
        this.acquireLockScript = RedisScript.of(ACQUIRE_LOCK_SCRIPT, Long.class);
        this.releaseLockScript = RedisScript.of(RELEASE_LOCK_SCRIPT, Long.class);
    }
    
    @Override
    public LockType getSupportedType() {
        return LockType.REDIS_LUA;
    }
    
    /**
     * Lua 스크립트를 사용하여 원자적으로 락을 획득합니다.
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
            
            log.debug("Attempting to acquire Redis Lua lock: key={}, timeout={}s, ownerId={}", 
                    lockKey, effectiveTimeout, ownerId);
            
            // Lua 스크립트 실행
            Long result = redisTemplate.execute(
                    acquireLockScript,
                    Collections.singletonList(lockKey),
                    ownerId,
                    String.valueOf(effectiveTimeout)
            );
            
            boolean acquired = result != null && result == 1;
            
            if (acquired) {
                // 소유자 ID 저장 (해제 시 사용)
                lockOwnerMap.put(lockKey, ownerId);
                log.debug("Successfully acquired Redis Lua lock: key={}, ownerId={}", lockKey, ownerId);
            } else {
                log.debug("Failed to acquire Redis Lua lock (already held): key={}", lockKey);
            }
            
            return acquired;
            
        } catch (Exception e) {
            log.error("Error while acquiring Redis Lua lock: key={}", lockKey, e);
            throw new LockConnectionException("Redis", lockKey, e);
        }
    }
    
    /**
     * Lua 스크립트를 사용하여 락을 해제합니다.
     * 소유자 검증을 통해 자신이 획득한 락만 해제할 수 있습니다.
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
            
            log.debug("Attempting to release Redis Lua lock: key={}, ownerId={}", lockKey, ownerId);
            
            // Lua 스크립트 실행 (소유자 검증 포함)
            Long result = redisTemplate.execute(
                    releaseLockScript,
                    Collections.singletonList(lockKey),
                    ownerId
            );
            
            boolean released = result != null && result == 1;
            
            if (released) {
                lockOwnerMap.remove(lockKey);
                log.debug("Successfully released Redis Lua lock: key={}", lockKey);
            } else {
                log.warn("Failed to release Redis Lua lock (owner mismatch or not exists): key={}", lockKey);
            }
            
            return released;
            
        } catch (Exception e) {
            log.error("Error while releasing Redis Lua lock: key={}", lockKey, e);
            throw new LockConnectionException("Redis", lockKey, e);
        }
    }
    
    /**
     * 특정 소유자 ID로 락 해제를 시도합니다.
     * 테스트 및 특수 상황에서 사용됩니다.
     * 
     * @param lockKey 락 식별자
     * @param ownerId 소유자 ID
     * @return 락 해제 성공 여부
     */
    public boolean releaseLockWithOwner(String lockKey, String ownerId) {
        try {
            log.debug("Attempting to release Redis Lua lock with specific owner: key={}, ownerId={}", 
                    lockKey, ownerId);
            
            Long result = redisTemplate.execute(
                    releaseLockScript,
                    Collections.singletonList(lockKey),
                    ownerId
            );
            
            boolean released = result != null && result == 1;
            
            if (released) {
                lockOwnerMap.remove(lockKey);
                log.debug("Successfully released Redis Lua lock: key={}", lockKey);
            } else {
                log.warn("Failed to release Redis Lua lock (owner mismatch): key={}, ownerId={}", 
                        lockKey, ownerId);
            }
            
            return released;
            
        } catch (Exception e) {
            log.error("Error while releasing Redis Lua lock: key={}", lockKey, e);
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
            
            log.debug("Attempting to acquire Redis Lua lock with specific owner: key={}, timeout={}s, ownerId={}", 
                    lockKey, effectiveTimeout, ownerId);
            
            Long result = redisTemplate.execute(
                    acquireLockScript,
                    Collections.singletonList(lockKey),
                    ownerId,
                    String.valueOf(effectiveTimeout)
            );
            
            boolean acquired = result != null && result == 1;
            
            if (acquired) {
                lockOwnerMap.put(lockKey, ownerId);
                log.debug("Successfully acquired Redis Lua lock: key={}, ownerId={}", lockKey, ownerId);
            } else {
                log.debug("Failed to acquire Redis Lua lock (already held): key={}", lockKey);
            }
            
            return acquired;
            
        } catch (Exception e) {
            log.error("Error while acquiring Redis Lua lock: key={}", lockKey, e);
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
