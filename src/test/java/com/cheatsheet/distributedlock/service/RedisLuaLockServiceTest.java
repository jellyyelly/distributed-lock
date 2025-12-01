package com.cheatsheet.distributedlock.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.cheatsheet.distributedlock.RedisTestConfiguration;
import com.cheatsheet.distributedlock.config.RedisConfig;
import com.cheatsheet.distributedlock.enums.LockType;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis Lua Script 락 서비스 JUnit 5 테스트
 * 
 * Requirements 3.1, 3.2, 3.3, 3.4 검증
 */
@SpringJUnitConfig(classes = {
        RedisTestConfiguration.class,
        RedisAutoConfiguration.class,
        RedisConfig.class,
        RedisLuaLockService.class
})
@DisplayName("Redis Lua Script 락 서비스 테스트")
class RedisLuaLockServiceTest {
    
    @Autowired
    private RedisLuaLockService lockService;
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    private String testKey;
    
    @AfterEach
    void cleanup() {
        if (testKey != null) {
            lockService.releaseLock(testKey);
            redisTemplate.delete(testKey);
        }
    }
    
    @Nested
    @DisplayName("락 타입 검증")
    class LockTypeTests {
        
        @Test
        @DisplayName("지원하는 락 타입은 REDIS_LUA이다")
        void supportedTypeIsRedisLua() {
            assertThat(lockService.getSupportedType()).isEqualTo(LockType.REDIS_LUA);
        }
    }
    
    @Nested
    @DisplayName("락 획득 테스트 - Requirements 3.1")
    class LockAcquisitionTests {
        
        @Test
        @DisplayName("유효한 키와 타임아웃으로 락 획득 성공")
        void acquireLockWithValidKeyAndTimeout() {
            testKey = generateUniqueKey("acquire");
            
            boolean acquired = lockService.acquireLock(testKey, 10);
            
            assertThat(acquired).isTrue();
            assertThat(redisTemplate.hasKey(testKey)).isTrue();
        }
        
        @ParameterizedTest
        @ValueSource(ints = {5, 10, 30, 60})
        @DisplayName("다양한 타임아웃 값으로 락 획득 성공")
        void acquireLockWithVariousTimeouts(int timeout) {
            testKey = generateUniqueKey("timeout");
            
            boolean acquired = lockService.acquireLock(testKey, timeout);
            
            assertThat(acquired).isTrue();
            
            Long ttl = redisTemplate.getExpire(testKey, TimeUnit.SECONDS);
            assertThat(ttl).isNotNull();
            assertThat(ttl).isGreaterThanOrEqualTo(0);
            assertThat(ttl).isLessThanOrEqualTo(timeout);
        }
        
        @Test
        @DisplayName("타임아웃이 0이면 기본값 적용")
        void acquireLockWithZeroTimeoutUsesDefault() {
            testKey = generateUniqueKey("default-timeout");
            
            boolean acquired = lockService.acquireLock(testKey, 0);
            
            assertThat(acquired).isTrue();
            
            Long ttl = redisTemplate.getExpire(testKey, TimeUnit.SECONDS);
            assertThat(ttl).isNotNull();
            assertThat(ttl).isGreaterThan(0);
        }
        
        @Test
        @DisplayName("락 획득 시 소유자 ID가 Redis에 저장됨")
        void acquireLockStoresOwnerIdInRedis() {
            testKey = generateUniqueKey("owner");
            
            lockService.acquireLock(testKey, 10);
            
            String storedValue = redisTemplate.opsForValue().get(testKey);
            assertThat(storedValue).isNotNull();
            assertThat(storedValue).isNotEmpty();
        }
    }

    
    @Nested
    @DisplayName("소유자 검증 락 해제 테스트 - Requirements 3.2")
    class OwnerVerificationReleaseTests {
        
        @Test
        @DisplayName("올바른 소유자만 락 해제 가능")
        void onlyCorrectOwnerCanReleaseLock() {
            testKey = generateUniqueKey("owner-verify");
            String correctOwnerId = UUID.randomUUID().toString();
            String wrongOwnerId = UUID.randomUUID().toString();
            
            // 특정 소유자로 락 획득
            boolean acquired = lockService.acquireLockWithOwner(testKey, 30, correctOwnerId);
            assertThat(acquired).isTrue();
            
            // 잘못된 소유자로 해제 시도 - 실패해야 함
            boolean releasedByWrong = lockService.releaseLockWithOwner(testKey, wrongOwnerId);
            assertThat(releasedByWrong).isFalse();
            
            // 락이 여전히 존재해야 함
            assertThat(redisTemplate.hasKey(testKey)).isTrue();
            
            // 올바른 소유자로 해제 - 성공해야 함
            boolean releasedByCorrect = lockService.releaseLockWithOwner(testKey, correctOwnerId);
            assertThat(releasedByCorrect).isTrue();
            
            // 락이 삭제되어야 함
            assertThat(redisTemplate.hasKey(testKey)).isFalse();
        }
        
        @Test
        @DisplayName("획득한 락은 정상적으로 해제됨")
        void acquiredLockCanBeReleased() {
            testKey = generateUniqueKey("release");
            
            lockService.acquireLock(testKey, 10);
            boolean released = lockService.releaseLock(testKey);
            
            assertThat(released).isTrue();
            assertThat(redisTemplate.hasKey(testKey)).isFalse();
        }
        
        @Test
        @DisplayName("획득하지 않은 락 해제 시 실패")
        void releaseNonAcquiredLockFails() {
            testKey = generateUniqueKey("not-acquired");
            
            boolean released = lockService.releaseLock(testKey);
            
            assertThat(released).isFalse();
        }
    }
    
    @Nested
    @DisplayName("상호 배제 테스트 - Requirements 3.3")
    class MutualExclusionTests {
        
        @Test
        @DisplayName("이미 존재하는 락에 대한 획득 시도는 실패")
        void acquireExistingLockFails() {
            testKey = generateUniqueKey("mutex");
            
            // 첫 번째 락 획득
            boolean firstAcquired = lockService.acquireLock(testKey, 30);
            assertThat(firstAcquired).isTrue();
            
            // 두 번째 락 획득 시도 - 실패해야 함
            String secondOwnerId = UUID.randomUUID().toString();
            boolean secondAcquired = lockService.acquireLockWithOwner(testKey, 30, secondOwnerId);
            assertThat(secondAcquired).isFalse();
        }
        
        @Test
        @DisplayName("동시 락 획득 시 하나만 성공")
        void concurrentLockAcquisitionOnlyOneSucceeds() throws InterruptedException {
            testKey = generateUniqueKey("concurrent");
            int threadCount = 5;
            AtomicInteger successCount = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            
            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    try {
                        startLatch.await();
                        String ownerId = UUID.randomUUID().toString();
                        boolean acquired = lockService.acquireLockWithOwner(testKey, 10, ownerId);
                        if (acquired) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // 예외 무시
                    } finally {
                        doneLatch.countDown();
                    }
                }).start();
            }
            
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            
            // 정확히 1개의 스레드만 락 획득 성공
            assertThat(successCount.get()).isEqualTo(1);
        }
    }
    
    @Nested
    @DisplayName("자동 만료 테스트 - Requirements 3.4")
    class AutoExpirationTests {
        
        @Test
        @DisplayName("만료 시간 후 락이 자동으로 해제됨")
        void lockAutoExpiresAfterTimeout() throws InterruptedException {
            testKey = generateUniqueKey("expire");
            int shortTimeout = 1;
            
            boolean acquired = lockService.acquireLock(testKey, shortTimeout);
            assertThat(acquired).isTrue();
            assertThat(redisTemplate.hasKey(testKey)).isTrue();
            
            // 만료 대기
            Thread.sleep(1500);
            
            // 락이 자동으로 삭제되어야 함
            assertThat(redisTemplate.hasKey(testKey)).isFalse();
        }
        
        @Test
        @DisplayName("만료 후 다른 클라이언트가 락 획득 가능")
        void otherClientCanAcquireAfterExpiration() throws InterruptedException {
            testKey = generateUniqueKey("expire-reacquire");
            int shortTimeout = 1;
            
            // 첫 번째 클라이언트 락 획득
            String firstOwnerId = UUID.randomUUID().toString();
            boolean firstAcquired = lockService.acquireLockWithOwner(testKey, shortTimeout, firstOwnerId);
            assertThat(firstAcquired).isTrue();
            
            // 만료 대기
            Thread.sleep(1500);
            
            // 두 번째 클라이언트 락 획득 - 성공해야 함
            String secondOwnerId = UUID.randomUUID().toString();
            boolean secondAcquired = lockService.acquireLockWithOwner(testKey, 30, secondOwnerId);
            assertThat(secondAcquired).isTrue();
        }
    }
    
    private String generateUniqueKey(String prefix) {
        return "test:lua:" + prefix + ":" + UUID.randomUUID();
    }
}
