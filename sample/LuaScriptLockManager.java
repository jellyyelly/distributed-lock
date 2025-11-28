package com.inet.api.common.lock;

import static com.inet.api.common.log.LogMarker.ERROR_MARKER;
import static com.inet.api.common.log.LogMarker.INFO_MARKER;
import static com.inet.api.common.log.LogMarker.WARN_MARKER;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LuaScriptLockManager {

    // 락 획득 Lua Script
    private static final String ACQUIRE_LOCK_SCRIPT = """
        local lockKey = KEYS[1]
        local validation = ARGV[1]
        local ttl = ARGV[2]
        
        if redis.call('GET', lockKey) == false then
            redis.call('SET', lockKey, validation, 'EX', ttl)
            return 1
        else
            return 0
        end
        """;

    // 락 해제 Lua Script
    private static final String RELEASE_LOCK_SCRIPT = """
        local lockKey = KEYS[1]
        local validation = ARGV[1]
        
        if redis.call('GET', lockKey) == validation then
            return redis.call('DEL', lockKey)
        else
            return 0
        end
        """;

    private final StringRedisTemplate redisTemplate;
    private RedisScript<Long> acquireLockScript;
    private RedisScript<Long> releaseLockScript;

    @PostConstruct
    public void init() {
        this.acquireLockScript = RedisScript.of(ACQUIRE_LOCK_SCRIPT, Long.class);
        this.releaseLockScript = RedisScript.of(RELEASE_LOCK_SCRIPT, Long.class);
    }

    public SimpleDistributedLock tryLockBy(String lockKey, String cronExpression) {
        try {
            String lockValue = UUID.randomUUID().toString();
            long ttl = calculateTtlFromCron(cronExpression);

            Long result = redisTemplate.execute(
                    acquireLockScript,
                    Collections.singletonList(lockKey),
                    lockValue,
                    String.valueOf(ttl)
            );

            if (result == 1) {
                log.info(INFO_MARKER, "Lua Script Lock 획득 current-thread: {}", Thread.currentThread().getName());

                return new SimpleDistributedLock(lockKey, lockValue, ttl, this);
            }
        } catch (Exception e) {
            log.error(ERROR_MARKER, "Lua Script Lock 획득 실패 lockKey={}, cronExpression={}", lockKey, cronExpression, e);
            throw e;
        }

        // 분산 락 획득 실패
        return null;
    }

    public SimpleDistributedLock tryLockBy(String lockKey, Duration ttl) {
        try {
            String lockValue = UUID.randomUUID().toString();
            long ttlSeconds = ttl.getSeconds();

            Long result = redisTemplate.execute(
                    acquireLockScript,
                    Collections.singletonList(lockKey),
                    lockValue,
                    String.valueOf(ttl)
            );

            if (result == 1) {
                log.info(INFO_MARKER, "Lua Script Lock 획득 current-thread: {}", Thread.currentThread().getName());

                return new SimpleDistributedLock(lockKey, lockValue, ttlSeconds, this);
            }
        } catch (Exception e) {
            log.error(ERROR_MARKER, "Lua Script Lock 획득 실패 lockKey={}, ttlSeconds={}", lockKey, ttl.getSeconds(), e);
            throw e;
        }

        // 분산 락 획득 실패
        return null;
    }

    public boolean releaseLock(SimpleDistributedLock lock) {
        if (lock == null) {
            return false;
        }

        try {
            Long result = redisTemplate.execute(
                    releaseLockScript,
                    Collections.singletonList(lock.getLockKey()),
                    lock.getLockValue()
            );

            return result == 1;
        } catch (Exception e) {
            log.error("Lua Script Lock 해제 실패: {}", lock.getLockKey(), e);
            return false;
        }
    }

    private long calculateTtlFromCron(String cronExpression) {
        try {
            CronExpression cron = CronExpression.parse(cronExpression);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextExecution = cron.next(now);

            if (nextExecution == null) {
                log.warn(WARN_MARKER, "다음 실행 시간을 계산할 수 없습니다: {}", cronExpression);
                return 300; // 기본값 5분
            }

            long intervalSeconds = Duration.between(now, nextExecution).getSeconds();
            return Math.max(intervalSeconds * 95 / 100, 30);  // 다음 실행 시간의 95% (안전 마진)와 30초 중 큰 값을 반환
        } catch (Exception e) {
            log.warn(WARN_MARKER, "Cron 표현식 파싱 실패: {}", cronExpression, e);
            return 300; // 기본값 5분
        }
    }
}
