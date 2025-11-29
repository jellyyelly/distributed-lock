package com.cheetsheet.distributedlock.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.cheatsheet.distributedlock.enums.LockType;
import com.cheatsheet.distributedlock.service.PostgresAdvisoryLockService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL Advisory Lock 서비스 JUnit 5 테스트
 * Requirements 2.1, 2.2, 2.3, 2.4 검증
 */
@Testcontainers
@DisplayName("PostgreSQL Advisory Lock 서비스 테스트")
class PostgresAdvisoryLockServiceTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("lockdb")
            .withUsername("postgres")
            .withPassword("postgres");
    
    private PostgresAdvisoryLockService lockService;
    private SingleConnectionDataSource dataSource;
    private String testKey;
    
    @BeforeEach
    void setUp() {
        // PostgreSQL Advisory Lock은 세션 레벨에서 관리되므로 단일 연결을 유지해야 함
        dataSource = new SingleConnectionDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        dataSource.setSuppressClose(true); // 연결이 닫히지 않도록 설정
        
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        lockService = new PostgresAdvisoryLockService(jdbcTemplate);
    }

    
    @AfterEach
    void cleanup() {
        if (testKey != null) {
            lockService.releaseLock(testKey);
        }
        // 연결 정리
        if (dataSource != null) {
            dataSource.destroy();
        }
    }
    
    @Nested
    @DisplayName("락 타입 검증")
    class LockTypeTests {
        @Test
        @DisplayName("지원하는 락 타입은 POSTGRES_ADVISORY이다")
        void supportedTypeIsPostgresAdvisory() {
            assertThat(lockService.getSupportedType()).isEqualTo(LockType.POSTGRES_ADVISORY);
        }
    }
    
    @Nested
    @DisplayName("논블로킹 락 획득 테스트 - Requirements 2.2")
    class NonBlockingAcquisitionTests {
        @Test
        @DisplayName("논블로킹 방식으로 락 획득 시 즉시 결과 반환")
        void nonBlockingLockReturnsImmediately() {
            testKey = generateUniqueKey("nonblocking");
            long startTime = System.currentTimeMillis();
            boolean acquired = lockService.acquireLock(testKey, 10);
            long elapsedTime = System.currentTimeMillis() - startTime;
            assertThat(acquired).isTrue();
            assertThat(elapsedTime).isLessThan(1000L);
        }
        
        @ParameterizedTest
        @ValueSource(ints = {1, 5, 10, 30})
        @DisplayName("다양한 타임아웃 값으로 락 획득 성공")
        void acquireLockWithVariousTimeouts(int timeout) {
            testKey = generateUniqueKey("timeout");
            boolean acquired = lockService.acquireLock(testKey, timeout);
            assertThat(acquired).isTrue();
        }
    }
    
    @Nested
    @DisplayName("락 해제 테스트 - Requirements 2.3")
    class LockReleaseTests {
        @Test
        @DisplayName("획득한 락은 정상적으로 해제됨")
        void acquiredLockCanBeReleased() {
            testKey = generateUniqueKey("release");
            boolean acquired = lockService.acquireLock(testKey, 10);
            assertThat(acquired).isTrue();
            boolean released = lockService.releaseLock(testKey);
            assertThat(released).isTrue();
        }
        
        @Test
        @DisplayName("해제 후 동일한 키로 다시 락 획득 가능")
        void canReacquireAfterRelease() {
            testKey = generateUniqueKey("reacquire");
            lockService.acquireLock(testKey, 10);
            lockService.releaseLock(testKey);
            boolean reacquired = lockService.acquireLock(testKey, 10);
            assertThat(reacquired).isTrue();
        }
    }
    
    @Nested
    @DisplayName("해시 변환 결정성 테스트 - Requirements 2.4")
    class HashConversionTests {
        @Test
        @DisplayName("동일한 문자열은 항상 동일한 해시값 반환")
        void sameStringProducesSameHash() {
            String lockKey = "test-key-for-hash";
            long hash1 = lockService.hashLockKey(lockKey);
            long hash2 = lockService.hashLockKey(lockKey);
            assertThat(hash1).isEqualTo(hash2);
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"key1", "key2", "product:123", "order:456"})
        @DisplayName("다양한 문자열에 대해 해시 변환 결정성 검증")
        void hashConversionIsDeterministic(String lockKey) {
            long hash1 = lockService.hashLockKey(lockKey);
            long hash2 = lockService.hashLockKey(lockKey);
            assertThat(hash1).isEqualTo(hash2);
        }
    }
    
    private String generateUniqueKey(String prefix) {
        return "test_postgres_" + prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
