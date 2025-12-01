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
 * Redis SETNX 락 서비스 Property-Based 테스트
 * JUnit5 ParameterizedTest를 사용한 속성 기반 테스트
 */
@SpringJUnitConfig(classes = {
        RedisTestConfiguration.class,
        RedisAutoConfiguration.class,
        RedisConfig.class,
        RedisSetnxLockService.class
})
@DisplayName("Redis SETNX 락 서비스 Property-Based 테스트")
class RedisSetnxLockServicePropertyTest {
    
    @Autowired
    private RedisSetnxLockService lockService;
    
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
    
    /**
     * 0 또는 음수 타임아웃 값을 제공하는 메서드 (기본 만료 시간 테스트용)
     */
    private static Stream<Arguments> provideInvalidTimeout() {
        return Stream.of(
                Arguments.of("defaultKey1", 0),
                Arguments.of("defaultKey2", -1),
                Arguments.of("defaultKey3", -10),
                Arguments.of("negativeTimeout", -5),
                Arguments.of("zeroTimeout", 0)
        );
    }
    
    @Nested
    @DisplayName("Property 13: SETNX 락 획득 및 만료 설정 - Requirements 4.1")
    class SetnxLockAcquisitionAndExpirationTests {
        
        /**
         * Feature: distributed-lock-samples, Property 13: SETNX 락 획득 및 만료 설정
         * 
         * For any valid Lock Key and Timeout value, when acquiring a lock using SETNX,
         * the system should use SET NX EX command to atomically set the lock with expiration.
         * 
         * Validates: Requirements 4.1
         */
        @ParameterizedTest(name = "락 키={0}, 타임아웃={1}초")
        @MethodSource("com.cheatsheet.distributedlock.service.RedisSetnxLockServicePropertyTest#provideLockKeyAndTimeout")
        @DisplayName("다양한 락 키와 타임아웃으로 SETNX 락 획득 및 만료 설정 검증")
        void setnxLockAcquisitionAndExpiration(String lockKey, int timeoutSeconds) {
            // 테스트 키 생성 (충돌 방지)
            testKey = "pbt:setnx:acquire:" + lockKey + ":" + UUID.randomUUID();
            
            // 락 획득 시도
            boolean acquired = lockService.acquireLock(testKey, timeoutSeconds);
            
            // 락 획득이 성공해야 함
            assertThat(acquired)
                    .as("락 획득이 성공해야 함")
                    .isTrue();
            
            // Redis에 키가 존재해야 함 (SET NX EX로 원자적으로 설정됨)
            Boolean keyExists = redisTemplate.hasKey(testKey);
            assertThat(keyExists)
                    .as("락 키가 Redis에 존재해야 함")
                    .isNotNull()
                    .isTrue();
            
            // 만료 시간이 설정되어야 함 (EX 옵션)
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
    @DisplayName("Property 14: SETNX 락 해제 - Requirements 4.2")
    class SetnxLockReleaseTests {
        
        /**
         * Feature: distributed-lock-samples, Property 14: SETNX 락 해제
         * 
         * For any acquired SETNX lock, when releasing the lock,
         * the system should use DEL command to remove the lock key.
         * 
         * Validates: Requirements 4.2
         */
        @ParameterizedTest(name = "락 키={0}, 타임아웃={1}초")
        @MethodSource("com.cheatsheet.distributedlock.service.RedisSetnxLockServicePropertyTest#provideLockKeyAndTimeout")
        @DisplayName("다양한 락 키와 타임아웃으로 SETNX 락 해제 검증")
        void setnxLockRelease(String lockKey, int timeoutSeconds) {
            // 테스트 키 생성 (충돌 방지)
            testKey = "pbt:setnx:release:" + lockKey + ":" + UUID.randomUUID();
            
            // 락 획득
            boolean acquired = lockService.acquireLock(testKey, timeoutSeconds);
            assertThat(acquired)
                    .as("락 획득이 성공해야 함")
                    .isTrue();
            
            // 락이 Redis에 존재하는지 확인
            Boolean keyExistsBeforeRelease = redisTemplate.hasKey(testKey);
            assertThat(keyExistsBeforeRelease)
                    .as("락 해제 전에 락 키가 Redis에 존재해야 함")
                    .isNotNull()
                    .isTrue();
            
            // 락 해제 (DEL 명령 사용)
            boolean released = lockService.releaseLock(testKey);
            
            // 락 해제가 성공해야 함
            assertThat(released)
                    .as("락 해제가 성공해야 함")
                    .isTrue();
            
            // 락이 Redis에서 삭제되었는지 확인
            Boolean keyExistsAfterRelease = redisTemplate.hasKey(testKey);
            assertThat(keyExistsAfterRelease)
                    .as("락 해제 후 락 키가 Redis에서 삭제되어야 함")
                    .isNotNull()
                    .isFalse();
            
            // 값도 조회되지 않아야 함
            String storedValue = redisTemplate.opsForValue().get(testKey);
            assertThat(storedValue)
                    .as("락 해제 후 값이 조회되지 않아야 함")
                    .isNull();
        }
    }
    
    @Nested
    @DisplayName("Property 15: SETNX 기존 키 실패 - Requirements 4.3")
    class SetnxExistingKeyFailureTests {
        
        /**
         * Feature: distributed-lock-samples, Property 15: SETNX 기존 키 실패
         * 
         * For any Lock Key that already exists, when executing SETNX command,
         * the acquisition should fail and return false.
         * 
         * Validates: Requirements 4.3
         */
        @ParameterizedTest(name = "락 키={0}, 타임아웃={1}초")
        @MethodSource("com.cheatsheet.distributedlock.service.RedisSetnxLockServicePropertyTest#provideLockKeyAndTimeout")
        @DisplayName("다양한 락 키와 타임아웃으로 SETNX 기존 키 실패 검증")
        void setnxExistingKeyFailure(String lockKey, int timeoutSeconds) {
            // 테스트 키 생성 (충돌 방지)
            testKey = "pbt:setnx:existing:" + lockKey + ":" + UUID.randomUUID();
            
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
            
            // 두 번째 클라이언트가 동일한 락 획득 시도 - 실패해야 함 (SETNX는 기존 키에 대해 실패)
            boolean secondAcquired = lockService.acquireLockWithOwner(testKey, timeoutSeconds, secondOwnerId);
            assertThat(secondAcquired)
                    .as("락이 이미 존재하므로 두 번째 클라이언트의 락 획득이 실패해야 함")
                    .isFalse();
            
            // 락의 소유자가 첫 번째 클라이언트인지 확인 (변경되지 않아야 함)
            String storedValue = redisTemplate.opsForValue().get(testKey);
            assertThat(storedValue)
                    .as("락의 소유자는 첫 번째 클라이언트여야 함 (변경되지 않음)")
                    .isNotNull()
                    .isEqualTo(firstOwnerId);
            
            // 락 해제
            lockService.releaseLock(testKey);
            
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
    @DisplayName("Property 16: SETNX 기본 만료 시간 적용 - Requirements 4.4")
    class SetnxDefaultExpirationTests {
        
        /**
         * Feature: distributed-lock-samples, Property 16: SETNX 기본 만료 시간 적용
         * 
         * For any lock acquisition request without a specified timeout or with a timeout of zero,
         * the system should apply a default expiration time to prevent deadlocks.
         * 
         * Validates: Requirements 4.4
         */
        @ParameterizedTest(name = "락 키={0}, 타임아웃={1}초")
        @MethodSource("com.cheatsheet.distributedlock.service.RedisSetnxLockServicePropertyTest#provideInvalidTimeout")
        @DisplayName("다양한 락 키와 0 또는 음수 타임아웃으로 기본 만료 시간 적용 검증")
        void setnxDefaultExpiration(String lockKey, int timeoutSeconds) {
            // 테스트 키 생성 (충돌 방지)
            testKey = "pbt:setnx:default:" + lockKey + ":" + UUID.randomUUID();
            
            // 0 또는 음수 타임아웃으로 락 획득 시도
            boolean acquired = lockService.acquireLock(testKey, timeoutSeconds);
            
            // 락 획득이 성공해야 함
            assertThat(acquired)
                    .as("락 획득이 성공해야 함")
                    .isTrue();
            
            // 락이 Redis에 존재하는지 확인
            Boolean keyExists = redisTemplate.hasKey(testKey);
            assertThat(keyExists)
                    .as("락 키가 Redis에 존재해야 함")
                    .isNotNull()
                    .isTrue();
            
            // 만료 시간이 설정되어야 함 (기본값 적용)
            Long ttl = redisTemplate.getExpire(testKey, TimeUnit.SECONDS);
            assertThat(ttl)
                    .as("기본 만료 시간이 설정되어야 함 (데드락 방지)")
                    .isNotNull()
                    .isGreaterThan(0L)  // 만료 시간이 설정되어 있어야 함
                    .isLessThanOrEqualTo(30L);  // 기본값은 30초
            
            // 소유자 ID가 저장되어야 함
            String storedValue = redisTemplate.opsForValue().get(testKey);
            assertThat(storedValue)
                    .as("소유자 ID가 저장되어야 함")
                    .isNotNull()
                    .isNotEmpty();
            
            // 락 해제
            lockService.releaseLock(testKey);
            
            // 락이 삭제되었는지 확인
            Boolean keyExistsAfterRelease = redisTemplate.hasKey(testKey);
            assertThat(keyExistsAfterRelease)
                    .as("락 해제 후 락 키가 Redis에서 삭제되어야 함")
                    .isNotNull()
                    .isFalse();
        }
    }
}
