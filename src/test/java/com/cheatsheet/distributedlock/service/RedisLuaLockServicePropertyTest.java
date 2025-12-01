package com.cheatsheet.distributedlock.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.cheatsheet.distributedlock.RedisTestConfiguration;
import com.cheatsheet.distributedlock.config.RedisConfig;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis Lua Script 락 서비스 Property-Based 테스트
 * JUnit5 ParameterizedTest를 사용한 속성 기반 테스트
 */
@SpringJUnitConfig(classes = {
        RedisTestConfiguration.class,
        RedisAutoConfiguration.class,
        RedisConfig.class,
        RedisLuaLockService.class
})
@DisplayName("Redis Lua Script 락 서비스 Property-Based 테스트")
class RedisLuaLockServicePropertyTest {
    
    @Autowired
    private RedisLuaLockService lockService;
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    private String testKey;
    
    @AfterEach
    void cleanup() {
        if (testKey != null) {
            redisTemplate.delete(testKey);
        }
    }
    
    /**
     * 다양한 락 키와 타임아웃 조합을 생성하는 메서드
     */
    private static Stream<Arguments> provideLockKeyAndTimeout() {
        return Stream.of(
                // 짧은 키, 다양한 타임아웃
                Arguments.of("A", 1),
                Arguments.of("B", 5),
                Arguments.of("C", 10),
                Arguments.of("key1", 15),
                Arguments.of("test", 20),
                
                // 중간 길이 키
                Arguments.of("testKey123", 30),
                Arguments.of("lockKey456", 40),
                Arguments.of("myLock789", 50),
                Arguments.of("distributedLock", 60),
                
                // 긴 키
                Arguments.of("veryLongLockKeyNameForTesting123456789", 25),
                Arguments.of("anotherVeryLongKeyWithManyCharacters", 35),
                
                // 숫자만 포함된 키
                Arguments.of("123456", 10),
                Arguments.of("999", 20),
                
                // 영문자와 숫자 혼합
                Arguments.of("abc123def456", 15),
                Arguments.of("test1key2lock3", 25)
        );
    }
    
    /**
     * 짧은 타임아웃 값을 제공하는 메서드 (자동 만료 테스트용)
     */
    private static Stream<Arguments> provideShortTimeout() {
        return Stream.of(
                Arguments.of("expireKey1", 1),
                Arguments.of("expireKey2", 2),
                Arguments.of("expireKey3", 3),
                Arguments.of("shortTTL", 1),
                Arguments.of("quickExpire", 2)
        );
    }
    
    @Nested
    @DisplayName("Property 9: Lua 스크립트 원자적 락 획득 - Requirements 3.1")
    class LuaScriptAtomicLockAcquisitionTests {
        
        /**
         * Feature: distributed-lock-samples, Property 9: Lua 스크립트 원자적 락 획득
         * 
         * For any valid Lock Key and Timeout value, when acquiring a lock using Lua script,
         * the system should atomically set the lock with the specified expiration time.
         * 
         * Validates: Requirements 3.1
         */
        @ParameterizedTest(name = "락 키={0}, 타임아웃={1}초")
        @MethodSource("com.cheatsheet.distributedlock.service.RedisLuaLockServicePropertyTest#provideLockKeyAndTimeout")
        @DisplayName("다양한 락 키와 타임아웃으로 원자적 락 획득 검증")
        void luaScriptAtomicLockAcquisition(String lockKey, int timeoutSeconds) {
            // 테스트 키 생성 (충돌 방지)
            testKey = "pbt:lua:atomic:" + lockKey + ":" + UUID.randomUUID();
            
            // 락 획득 시도
            boolean acquired = lockService.acquireLock(testKey, timeoutSeconds);
            
            // 락 획득이 성공해야 함
            assertThat(acquired)
                    .as("락 획득이 성공해야 함")
                    .isTrue();
            
            // Redis에 키가 존재해야 함 (원자적으로 설정됨)
            Boolean keyExists = redisTemplate.hasKey(testKey);
            assertThat(keyExists)
                    .as("락 키가 Redis에 존재해야 함")
                    .isNotNull()
                    .isTrue();
            
            // 만료 시간이 설정되어야 함
            Long ttl = redisTemplate.getExpire(testKey, TimeUnit.SECONDS);
            assertThat(ttl)
                    .as("만료 시간이 설정되어야 함")
                    .isNotNull()
                    .isGreaterThanOrEqualTo(0L)
                    .isLessThanOrEqualTo((long) timeoutSeconds);
            
            // 소유자 ID가 저장되어야 함
            String storedValue = redisTemplate.opsForValue().get(testKey);
            assertThat(storedValue)
                    .as("소유자 ID가 저장되어야 함")
                    .isNotNull()
                    .isNotEmpty();
            
            // 락 해제
            lockService.releaseLock(testKey);
        }
    }
    
    @Nested
    @DisplayName("Property 10: Lua 스크립트 소유자 검증 해제 - Requirements 3.2")
    class LuaScriptOwnerVerificationTests {
        
        /**
         * Feature: distributed-lock-samples, Property 10: Lua 스크립트 소유자 검증 해제
         * 
         * For any acquired lock with an owner identifier, when releasing the lock,
         * only the correct owner should be able to successfully release it;
         * attempts by other owners should fail.
         * 
         * Validates: Requirements 3.2
         */
        @ParameterizedTest(name = "락 키={0}, 타임아웃={1}초")
        @MethodSource("com.cheatsheet.distributedlock.service.RedisLuaLockServicePropertyTest#provideLockKeyAndTimeout")
        @DisplayName("다양한 락 키와 타임아웃으로 소유자 검증 해제 검증")
        void luaScriptOwnerVerificationOnRelease(String lockKey, int timeoutSeconds) {
            // 테스트 키 생성 (충돌 방지)
            testKey = "pbt:lua:owner:" + lockKey + ":" + UUID.randomUUID();
            
            // 두 개의 서로 다른 소유자 ID 생성
            String correctOwnerId = "owner-correct-" + System.nanoTime();
            String wrongOwnerId = "owner-wrong-" + System.nanoTime();
            
            // 올바른 소유자 ID로 락 획득
            boolean acquired = lockService.acquireLockWithOwner(testKey, timeoutSeconds, correctOwnerId);
            assertThat(acquired)
                    .as("락 획득이 성공해야 함")
                    .isTrue();
            
            // 잘못된 소유자 ID로 락 해제 시도 - 실패해야 함
            boolean releasedByWrongOwner = lockService.releaseLockWithOwner(testKey, wrongOwnerId);
            assertThat(releasedByWrongOwner)
                    .as("잘못된 소유자 ID로는 락 해제가 실패해야 함")
                    .isFalse();
            
            // 락이 여전히 존재해야 함
            Boolean keyStillExists = redisTemplate.hasKey(testKey);
            assertThat(keyStillExists)
                    .as("잘못된 소유자의 해제 시도 후에도 락이 여전히 존재해야 함")
                    .isNotNull()
                    .isTrue();
            
            // 올바른 소유자 ID로 락 해제 시도 - 성공해야 함
            boolean releasedByCorrectOwner = lockService.releaseLockWithOwner(testKey, correctOwnerId);
            assertThat(releasedByCorrectOwner)
                    .as("올바른 소유자 ID로는 락 해제가 성공해야 함")
                    .isTrue();
            
            // 락이 삭제되어야 함
            Boolean keyExistsAfterRelease = redisTemplate.hasKey(testKey);
            assertThat(keyExistsAfterRelease)
                    .as("올바른 소유자의 해제 후 락이 삭제되어야 함")
                    .isNotNull()
                    .isFalse();
        }
    }
    
    @Nested
    @DisplayName("Property 11: Redis 상호 배제 - Requirements 3.3")
    class RedisMutualExclusionTests {
        
        /**
         * Feature: distributed-lock-samples, Property 11: Redis 상호 배제
         * 
         * For any Lock Key, when a lock already exists and another client attempts
         * to acquire the same lock, the acquisition should fail.
         * 
         * Validates: Requirements 3.3
         */
        @ParameterizedTest(name = "락 키={0}, 타임아웃={1}초")
        @MethodSource("com.cheatsheet.distributedlock.service.RedisLuaLockServicePropertyTest#provideLockKeyAndTimeout")
        @DisplayName("다양한 락 키와 타임아웃으로 상호 배제 검증")
        void redisMutualExclusion(String lockKey, int timeoutSeconds) {
            // 테스트 키 생성 (충돌 방지)
            testKey = "pbt:lua:mutex:" + lockKey + ":" + UUID.randomUUID();
            
            // 두 개의 서로 다른 소유자 ID 생성 (두 클라이언트 시뮬레이션)
            String firstOwnerId = "owner-first-" + System.nanoTime();
            String secondOwnerId = "owner-second-" + System.nanoTime();
            
            // 첫 번째 클라이언트가 락 획득
            boolean firstAcquired = lockService.acquireLockWithOwner(testKey, timeoutSeconds, firstOwnerId);
            assertThat(firstAcquired)
                    .as("첫 번째 클라이언트의 락 획득이 성공해야 함")
                    .isTrue();
            
            // 락이 Redis에 존재하는지 확인
            Boolean keyExists = redisTemplate.hasKey(testKey);
            assertThat(keyExists)
                    .as("락 키가 Redis에 존재해야 함")
                    .isNotNull()
                    .isTrue();
            
            // 두 번째 클라이언트가 동일한 락 획득 시도 - 실패해야 함 (상호 배제)
            boolean secondAcquired = lockService.acquireLockWithOwner(testKey, timeoutSeconds, secondOwnerId);
            assertThat(secondAcquired)
                    .as("락이 이미 존재하므로 두 번째 클라이언트의 락 획득이 실패해야 함")
                    .isFalse();
            
            // 락의 소유자가 첫 번째 클라이언트인지 확인
            String storedValue = redisTemplate.opsForValue().get(testKey);
            assertThat(storedValue)
                    .as("락의 소유자는 첫 번째 클라이언트여야 함")
                    .isNotNull()
                    .isEqualTo(firstOwnerId);
            
            // 첫 번째 클라이언트가 락 해제
            boolean released = lockService.releaseLockWithOwner(testKey, firstOwnerId);
            assertThat(released)
                    .as("첫 번째 클라이언트의 락 해제가 성공해야 함")
                    .isTrue();
            
            // 락이 해제된 후 두 번째 클라이언트가 락 획득 시도 - 성공해야 함
            boolean secondAcquiredAfterRelease = lockService.acquireLockWithOwner(testKey, timeoutSeconds, secondOwnerId);
            assertThat(secondAcquiredAfterRelease)
                    .as("락이 해제된 후 두 번째 클라이언트의 락 획득이 성공해야 함")
                    .isTrue();
            
            // 두 번째 클라이언트가 락을 소유하고 있는지 확인
            String storedValueAfterRelease = redisTemplate.opsForValue().get(testKey);
            assertThat(storedValueAfterRelease)
                    .as("락의 소유자는 두 번째 클라이언트여야 함")
                    .isNotNull()
                    .isEqualTo(secondOwnerId);
        }
    }
    
    @Nested
    @DisplayName("Property 12: Redis 자동 만료 - Requirements 3.4")
    class RedisAutomaticExpirationTests {
        
        /**
         * Feature: distributed-lock-samples, Property 12: Redis 자동 만료
         * 
         * For any lock with a specified expiration time, after the expiration time elapses,
         * the lock should be automatically released and become available for other clients to acquire.
         * 
         * Validates: Requirements 3.4
         */
        @ParameterizedTest(name = "락 키={0}, 타임아웃={1}초")
        @MethodSource("com.cheatsheet.distributedlock.service.RedisLuaLockServicePropertyTest#provideShortTimeout")
        @DisplayName("다양한 락 키와 짧은 타임아웃으로 자동 만료 검증")
        void redisAutomaticExpiration(String lockKey, int timeoutSeconds) {
            // 테스트 키 생성 (충돌 방지)
            testKey = "pbt:lua:expiration:" + lockKey + ":" + UUID.randomUUID();
            
            // 두 개의 서로 다른 소유자 ID 생성
            String firstOwnerId = "owner-first-" + System.nanoTime();
            String secondOwnerId = "owner-second-" + System.nanoTime();
            
            try {
                // 첫 번째 클라이언트가 짧은 타임아웃으로 락 획득
                boolean firstAcquired = lockService.acquireLockWithOwner(testKey, timeoutSeconds, firstOwnerId);
                assertThat(firstAcquired)
                        .as("첫 번째 클라이언트의 락 획득이 성공해야 함")
                        .isTrue();
                
                // 락이 Redis에 존재하는지 확인
                Boolean keyExistsBeforeExpiration = redisTemplate.hasKey(testKey);
                assertThat(keyExistsBeforeExpiration)
                        .as("만료 전에 락 키가 Redis에 존재해야 함")
                        .isNotNull()
                        .isTrue();
                
                // TTL 확인 - 설정된 타임아웃 이하여야 함
                Long ttlBeforeExpiration = redisTemplate.getExpire(testKey, TimeUnit.SECONDS);
                assertThat(ttlBeforeExpiration)
                        .as("만료 전 TTL이 설정되어야 함")
                        .isNotNull()
                        .isGreaterThanOrEqualTo(0L)
                        .isLessThanOrEqualTo((long) timeoutSeconds);
                
                // 만료 시간보다 약간 더 대기 (만료 보장)
                Thread.sleep((timeoutSeconds + 1) * 1000L);
                
                // 만료 후 락이 자동으로 삭제되었는지 확인
                Boolean keyExistsAfterExpiration = redisTemplate.hasKey(testKey);
                assertThat(keyExistsAfterExpiration)
                        .as("만료 시간 경과 후 락이 자동으로 삭제되어야 함")
                        .isNotNull()
                        .isFalse();
                
                // 두 번째 클라이언트가 동일한 락 획득 시도 - 성공해야 함 (자동 만료로 인해 가능)
                boolean secondAcquired = lockService.acquireLockWithOwner(testKey, timeoutSeconds, secondOwnerId);
                assertThat(secondAcquired)
                        .as("만료 후 두 번째 클라이언트의 락 획득이 성공해야 함")
                        .isTrue();
                
                // 두 번째 클라이언트가 락을 소유하고 있는지 확인
                String storedValueAfterExpiration = redisTemplate.opsForValue().get(testKey);
                assertThat(storedValueAfterExpiration)
                        .as("만료 후 새로운 락의 소유자는 두 번째 클라이언트여야 함")
                        .isNotNull()
                        .isEqualTo(secondOwnerId);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("테스트 중 인터럽트 발생", e);
            }
        }
    }
}
