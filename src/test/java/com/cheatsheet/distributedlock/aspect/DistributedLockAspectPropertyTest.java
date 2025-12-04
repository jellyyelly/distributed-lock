package com.cheatsheet.distributedlock.aspect;

import com.cheatsheet.distributedlock.annotation.DistributedLock;
import com.cheatsheet.distributedlock.enums.LockType;
import com.cheatsheet.distributedlock.exception.LockAcquisitionException;
import com.cheatsheet.distributedlock.service.DistributedLockService;
import com.cheatsheet.distributedlock.service.LockRetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DistributedLockAspect의 Property-Based 테스트
 * 
 * 각 테스트는 최소 100회 반복 실행되어 다양한 입력값에 대한 속성을 검증합니다.
 */
@SpringBootTest(classes = {
        DistributedLockAspectPropertyTest.TestConfig.class,
        DistributedLockAspectPropertyTest.TestServiceWithLock.class
})
@DisplayName("DistributedLockAspect Property 테스트")
class DistributedLockAspectPropertyTest {
    
    @Autowired
    private TestServiceWithLock testService;
    
    @Autowired
    private DistributedLockService mockLockService;
    
    @BeforeEach
    void setUp() {
        // 각 테스트 전에 mock 초기화
        reset(mockLockService);
    }
    
    @RepeatedTest(100)
    @DisplayName("Property 19: AOP 메서드 인터셉션 - 메서드 실행 전 락 획득")
    // Feature: distributed-lock-samples, Property 19: AOP 메서드 인터셉션
    void aopMethodInterception() {
        // Given: 랜덤 락 키
        String lockKey = "test:" + UUID.randomUUID().toString().substring(0, 10);
        int expectedTimeout = 10; // 애너테이션에 정의된 timeout 값
        
        // Mock 설정: 락 획득 성공
        when(mockLockService.getSupportedType()).thenReturn(LockType.REDIS_LUA);
        when(mockLockService.acquireLock(anyString(), anyInt())).thenReturn(true);
        when(mockLockService.releaseLock(anyString())).thenReturn(true);
        
        // When: @DistributedLock 애너테이션이 붙은 메서드 호출
        String result = testService.methodWithLock(lockKey, 30);
        
        // Then: 메서드가 정상적으로 실행되어야 함
        assertThat(result).isEqualTo("success");
        
        // Then: 락 획득이 메서드 실행 전에 호출되어야 함 (애너테이션의 timeout 사용)
        verify(mockLockService, times(1)).acquireLock(eq(lockKey), eq(expectedTimeout));
        
        // Then: 락 해제가 메서드 실행 후에 호출되어야 함
        verify(mockLockService, times(1)).releaseLock(eq(lockKey));
    }
    
    @RepeatedTest(100)
    @DisplayName("Property 21: 락 타입 기반 서비스 선택")
    // Feature: distributed-lock-samples, Property 21: 락 타입 기반 서비스 선택
    void lockTypeBasedServiceSelection() {
        // Given: 랜덤 락 키
        String lockKey = "test:" + UUID.randomUUID().toString().substring(0, 10);
        
        // Mock 설정: REDIS_LUA 타입 지원
        when(mockLockService.getSupportedType()).thenReturn(LockType.REDIS_LUA);
        when(mockLockService.acquireLock(anyString(), anyInt())).thenReturn(true);
        when(mockLockService.releaseLock(anyString())).thenReturn(true);
        
        // When: REDIS_LUA 타입으로 메서드 호출
        testService.methodWithRedisLuaLock(lockKey);
        
        // Then: 올바른 Lock Service가 선택되어 사용되어야 함
        // Aspect가 LockType.REDIS_LUA에 해당하는 서비스를 선택하고 사용해야 함
        verify(mockLockService, times(1)).acquireLock(eq(lockKey), anyInt());
        verify(mockLockService, times(1)).releaseLock(eq(lockKey));
    }
    
    @RepeatedTest(100)
    @DisplayName("Property 22: 락 해제 보장 - 정상 실행 시")
    // Feature: distributed-lock-samples, Property 22: 락 해제 보장
    void lockReleaseGuaranteeOnSuccess() {
        // Given: 랜덤 락 키
        String lockKey = "test:" + UUID.randomUUID().toString().substring(0, 10);
        
        // Mock 설정
        when(mockLockService.getSupportedType()).thenReturn(LockType.REDIS_LUA);
        when(mockLockService.acquireLock(anyString(), anyInt())).thenReturn(true);
        when(mockLockService.releaseLock(anyString())).thenReturn(true);
        
        // When: 메서드가 정상적으로 실행됨
        testService.methodWithLock(lockKey, 10);
        
        // Then: 락이 반드시 해제되어야 함
        verify(mockLockService, times(1)).releaseLock(eq(lockKey));
    }
    
    @RepeatedTest(100)
    @DisplayName("Property 22: 락 해제 보장 - 예외 발생 시")
    // Feature: distributed-lock-samples, Property 22: 락 해제 보장
    void lockReleaseGuaranteeOnException() {
        // Given: 랜덤 락 키
        String lockKey = "test:" + UUID.randomUUID().toString().substring(0, 10);
        
        // Mock 설정
        when(mockLockService.getSupportedType()).thenReturn(LockType.REDIS_LUA);
        when(mockLockService.acquireLock(anyString(), anyInt())).thenReturn(true);
        when(mockLockService.releaseLock(anyString())).thenReturn(true);
        
        // When: 메서드 실행 중 예외 발생
        assertThatThrownBy(() -> testService.methodThatThrowsException(lockKey))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Test exception");
        
        // Then: 예외가 발생해도 락이 반드시 해제되어야 함
        verify(mockLockService, times(1)).releaseLock(eq(lockKey));
    }
    
    @RepeatedTest(100)
    @DisplayName("Property 23: 재시도 로직 - 성공할 때까지 재시도")
    // Feature: distributed-lock-samples, Property 23: 재시도 로직
    void retryLogicUntilSuccess() {
        // Given: 랜덤 락 키
        String lockKey = "test:" + UUID.randomUUID().toString().substring(0, 10);
        // methodWithRetry는 retryCount=3으로 고정되어 있음 (최대 4번 시도)
        int maxAttempts = 4; // 최초 시도 1 + 재시도 3
        int successAttempt = ThreadLocalRandom.current().nextInt(1, maxAttempts + 1);
        
        // Mock 설정: successAttempt번째 시도에서 성공
        when(mockLockService.getSupportedType()).thenReturn(LockType.REDIS_LUA);
        AtomicInteger attemptCounter = new AtomicInteger(0);
        when(mockLockService.acquireLock(anyString(), anyInt())).thenAnswer(invocation -> {
            int attempt = attemptCounter.incrementAndGet();
            return attempt >= successAttempt;
        });
        when(mockLockService.releaseLock(anyString())).thenReturn(true);
        
        // When: 재시도 설정이 있는 메서드 호출
        String result = testService.methodWithRetry(lockKey);
        
        // Then: 메서드가 성공적으로 실행되어야 함
        assertThat(result).isEqualTo("success");
        
        // Then: 성공할 때까지 재시도해야 함
        verify(mockLockService, times(successAttempt)).acquireLock(eq(lockKey), anyInt());
    }
    
    @RepeatedTest(100)
    @DisplayName("Property 23: 재시도 로직 - 모든 재시도 실패 시 예외 발생")
    // Feature: distributed-lock-samples, Property 23: 재시도 로직
    void retryLogicFailureAfterAllAttempts() {
        // Given: 랜덤 락 키
        String lockKey = "test:" + UUID.randomUUID().toString().substring(0, 10);
        // methodWithRetry는 retryCount=3으로 고정되어 있음 (최대 4번 시도)
        int expectedAttempts = 4; // 최초 시도 1 + 재시도 3
        
        // Mock 설정: 모든 시도 실패
        when(mockLockService.getSupportedType()).thenReturn(LockType.REDIS_LUA);
        when(mockLockService.acquireLock(anyString(), anyInt())).thenReturn(false);
        
        // When & Then: 모든 재시도 실패 시 예외 발생
        assertThatThrownBy(() -> testService.methodWithRetry(lockKey))
                .isInstanceOf(LockAcquisitionException.class)
                .hasMessageContaining("Failed to acquire lock after retries");
        
        // Then: 최초 시도 + 재시도 횟수만큼 시도해야 함
        verify(mockLockService, times(expectedAttempts)).acquireLock(eq(lockKey), anyInt());
    }
    
    @RepeatedTest(100)
    @DisplayName("Property 20: SpEL 동적 락 키 생성")
    // Feature: distributed-lock-samples, Property 20: SpEL 동적 락 키 생성
    void spelDynamicLockKeyGeneration() {
        // Given: 랜덤 제품 ID
        String productId = "product-" + UUID.randomUUID().toString().substring(0, 8);
        String expectedLockKey = "product:" + productId;
        
        // Mock 설정
        when(mockLockService.getSupportedType()).thenReturn(LockType.REDIS_LUA);
        when(mockLockService.acquireLock(anyString(), anyInt())).thenReturn(true);
        when(mockLockService.releaseLock(anyString())).thenReturn(true);
        
        // When: SpEL 표현식을 사용하는 메서드 호출
        testService.methodWithSpelKey(productId);
        
        // Then: SpEL 표현식이 올바르게 평가되어 락 키가 생성되어야 함
        verify(mockLockService, times(1)).acquireLock(eq(expectedLockKey), anyInt());
        verify(mockLockService, times(1)).releaseLock(eq(expectedLockKey));
    }
    
    /**
     * 테스트용 서비스 클래스
     */
    @Service
    public static class TestServiceWithLock {
        
        @DistributedLock(key = "#lockKey", type = LockType.REDIS_LUA, timeout = 10)
        public String methodWithLock(String lockKey, int timeout) {
            return "success";
        }
        
        @DistributedLock(key = "#lockKey", type = LockType.MYSQL_SESSION, timeout = 10)
        public String methodWithMysqlLock(String lockKey) {
            return "success";
        }
        
        @DistributedLock(key = "#lockKey", type = LockType.POSTGRES_ADVISORY, timeout = 10)
        public String methodWithPostgresLock(String lockKey) {
            return "success";
        }
        
        @DistributedLock(key = "#lockKey", type = LockType.REDIS_LUA, timeout = 10)
        public String methodWithRedisLuaLock(String lockKey) {
            return "success";
        }
        
        @DistributedLock(key = "#lockKey", type = LockType.REDIS_SETNX, timeout = 10)
        public String methodWithRedisSetnxLock(String lockKey) {
            return "success";
        }
        
        @DistributedLock(key = "#lockKey", type = LockType.REDIS_LUA, timeout = 10)
        public String methodThatThrowsException(String lockKey) {
            throw new RuntimeException("Test exception");
        }
        
        @DistributedLock(
                key = "#lockKey", 
                type = LockType.REDIS_LUA, 
                timeout = 10,
                retryCount = 3,
                retryInterval = 50
        )
        public String methodWithRetry(String lockKey) {
            return "success";
        }
        
        @DistributedLock(key = "'product:' + #productId", type = LockType.REDIS_LUA, timeout = 10)
        public String methodWithSpelKey(String productId) {
            return "success";
        }
    }
    
    /**
     * 테스트 설정
     */
    @Configuration
    @EnableAspectJAutoProxy
    @EnableRetry
    public static class TestConfig {
        
        @Bean
        public DistributedLockService mockLockService() {
            DistributedLockService mock = mock(DistributedLockService.class);
            // 기본 동작 설정: REDIS_LUA 타입 지원
            when(mock.getSupportedType()).thenReturn(LockType.REDIS_LUA);
            return mock;
        }
        
        @Bean
        public LockRetryService lockRetryService() {
            return new LockRetryService();
        }
        
        @Bean
        public DistributedLockAspect distributedLockAspect(
                DistributedLockService mockLockService,
                LockRetryService lockRetryService) {
            return new DistributedLockAspect(java.util.List.of(mockLockService), lockRetryService);
        }
 
    }
}
