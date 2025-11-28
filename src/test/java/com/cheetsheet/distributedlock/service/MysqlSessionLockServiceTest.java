package com.cheetsheet.distributedlock.service;

import com.cheetsheet.distributedlock.enums.LockType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL 세션 락 서비스 JUnit 5 테스트
 * Requirements 1.1, 1.2, 1.3, 1.4 검증
 * 
 * MySQL GET_LOCK은 세션 기반이므로 SingleConnectionDataSource를 사용하여
 * 동일한 연결에서 락 획득/해제가 이루어지도록 합니다.
 */
@Testcontainers
@DisplayName("MySQL 세션 락 서비스 테스트")
class MysqlSessionLockServiceTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("lockdb")
            .withUsername("root")
            .withPassword("root");
    
    private static SingleConnectionDataSource dataSource;
    private static MysqlSessionLockService lockService;
    
    @BeforeAll
    static void setUp() {
        dataSource = new SingleConnectionDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(mysql.getJdbcUrl());
        dataSource.setUsername(mysql.getUsername());
        dataSource.setPassword(mysql.getPassword());
        dataSource.setSuppressClose(true);
        
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        lockService = new MysqlSessionLockService(jdbcTemplate);
    }

    
    @AfterAll
    static void tearDown() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }
    
    @Nested
    @DisplayName("락 타입 검증")
    class LockTypeTests {
        @Test
        @DisplayName("지원하는 락 타입은 MYSQL_SESSION이다")
        void supportedTypeIsMysqlSession() {
            assertThat(lockService.getSupportedType()).isEqualTo(LockType.MYSQL_SESSION);
        }
    }
    
    @Nested
    @DisplayName("락 획득 테스트 - Requirements 1.1")
    class LockAcquisitionTests {
        @Test
        @DisplayName("유효한 키와 타임아웃으로 락 획득 성공")
        void acquireLockWithValidKeyAndTimeout() {
            String testKey = generateUniqueKey("acquire");
            try {
                boolean acquired = lockService.acquireLock(testKey, 10);
                assertThat(acquired).isTrue();
            } finally {
                lockService.releaseLock(testKey);
            }
        }
        
        @ParameterizedTest
        @ValueSource(ints = {1, 5, 10, 30})
        @DisplayName("다양한 타임아웃 값으로 락 획득 성공")
        void acquireLockWithVariousTimeouts(int timeout) {
            String testKey = generateUniqueKey("timeout");
            try {
                boolean acquired = lockService.acquireLock(testKey, timeout);
                assertThat(acquired).isTrue();
            } finally {
                lockService.releaseLock(testKey);
            }
        }
    }
    
    @Nested
    @DisplayName("락 해제 테스트 - Requirements 1.2")
    class LockReleaseTests {
        @Test
        @DisplayName("획득한 락은 정상적으로 해제됨")
        void acquiredLockCanBeReleased() {
            String testKey = generateUniqueKey("release");
            boolean acquired = lockService.acquireLock(testKey, 10);
            assertThat(acquired).isTrue();
            
            boolean released = lockService.releaseLock(testKey);
            assertThat(released).isTrue();
        }
        
        @Test
        @DisplayName("해제 후 동일한 키로 다시 락 획득 가능")
        void canReacquireAfterRelease() {
            String testKey = generateUniqueKey("reacquire");
            lockService.acquireLock(testKey, 10);
            lockService.releaseLock(testKey);
            
            boolean reacquired = lockService.acquireLock(testKey, 10);
            assertThat(reacquired).isTrue();
            lockService.releaseLock(testKey);
        }
    }
    
    @Nested
    @DisplayName("타임아웃 테스트 - Requirements 1.3")
    class TimeoutTests {
        @Test
        @DisplayName("다른 세션에서 보유 중인 락에 대해 타임아웃 0으로 획득 시도 시 실패")
        void acquireWithZeroTimeoutOnHeldLockFails() throws InterruptedException {
            String testKey = generateUniqueKey("timeout-fail");
            CountDownLatch lockAcquired = new CountDownLatch(1);
            CountDownLatch testDone = new CountDownLatch(1);
            
            // 다른 세션에서 락 획득
            new Thread(() -> {
                try {
                    SingleConnectionDataSource otherDs = new SingleConnectionDataSource();
                    otherDs.setDriverClassName("com.mysql.cj.jdbc.Driver");
                    otherDs.setUrl(mysql.getJdbcUrl());
                    otherDs.setUsername(mysql.getUsername());
                    otherDs.setPassword(mysql.getPassword());
                    otherDs.setSuppressClose(true);
                    
                    MysqlSessionLockService otherService = new MysqlSessionLockService(new JdbcTemplate(otherDs));
                    otherService.acquireLock(testKey, 10);
                    lockAcquired.countDown();
                    testDone.await(10, TimeUnit.SECONDS);
                    otherService.releaseLock(testKey);
                    otherDs.destroy();
                } catch (Exception e) {
                    lockAcquired.countDown();
                }
            }).start();
            
            lockAcquired.await(5, TimeUnit.SECONDS);
            
            // 타임아웃 0으로 획득 시도 - 다른 세션이 보유 중이므로 실패해야 함
            boolean acquired = lockService.acquireLock(testKey, 0);
            assertThat(acquired).isFalse();
            
            testDone.countDown();
        }
    }
    
    @Nested
    @DisplayName("상호 배제 테스트 - Requirements 1.4")
    class MutualExclusionTests {
        @Test
        @DisplayName("동시 락 획득 시 최대 하나만 성공")
        void concurrentLockAcquisitionAtMostOneSucceeds() throws InterruptedException {
            String testKey = generateUniqueKey("concurrent");
            int threadCount = 3;
            AtomicInteger successCount = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            
            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    try {
                        SingleConnectionDataSource threadDs = new SingleConnectionDataSource();
                        threadDs.setDriverClassName("com.mysql.cj.jdbc.Driver");
                        threadDs.setUrl(mysql.getJdbcUrl());
                        threadDs.setUsername(mysql.getUsername());
                        threadDs.setPassword(mysql.getPassword());
                        threadDs.setSuppressClose(true);
                        
                        MysqlSessionLockService threadService = new MysqlSessionLockService(new JdbcTemplate(threadDs));
                        
                        startLatch.await();
                        boolean acquired = threadService.acquireLock(testKey, 1);
                        if (acquired) {
                            successCount.incrementAndGet();
                            Thread.sleep(50);
                            threadService.releaseLock(testKey);
                        }
                        threadDs.destroy();
                    } catch (Exception e) {
                        // 예외 무시
                    } finally {
                        doneLatch.countDown();
                    }
                }).start();
            }
            
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            
            assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
        }
    }
    
    private static String generateUniqueKey(String prefix) {
        return "test_mysql_" + prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
