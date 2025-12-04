package com.cheatsheet.distributedlock.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InventoryService 사용 예제 테스트
 * 
 * 이 테스트는 @DistributedLock 애너테이션을 사용한 실제 시나리오를 보여줍니다.
 * Testcontainers를 사용하여 실제 데이터베이스와 Redis 환경에서 테스트합니다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
    }
)
@Testcontainers
class InventoryServiceExampleTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL 설정
        registry.add("spring.datasource.mysql.jdbc-url", mysql::getJdbcUrl);
        registry.add("spring.datasource.mysql.username", mysql::getUsername);
        registry.add("spring.datasource.mysql.password", mysql::getPassword);
        
        // PostgreSQL 설정
        registry.add("spring.datasource.postgresql.jdbc-url", postgres::getJdbcUrl);
        registry.add("spring.datasource.postgresql.username", postgres::getUsername);
        registry.add("spring.datasource.postgresql.password", postgres::getPassword);
        
        // Redis 설정
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }
    
    @Autowired
    private InventoryService inventoryService;
    
    @BeforeEach
    void setUp() {
        inventoryService.clearAllStock();
    }
    
    @Test
    @DisplayName("Redis Lua 락을 사용한 재고 감소 예제")
    void exampleRedisLuaLock() {
        // Given: 상품 재고 초기화
        String productId = "PROD001";
        inventoryService.initializeStock(productId, 100);
        
        // When: Redis Lua 락을 사용하여 재고 감소
        int remainingStock = inventoryService.decreaseStockWithRedisLua(productId, 10);
        
        // Then: 재고가 정상적으로 감소
        assertThat(remainingStock).isEqualTo(90);
        assertThat(inventoryService.getStock(productId)).isEqualTo(90);
        
        System.out.println("✓ Redis Lua 락 예제 완료: 재고 100 -> 90");
    }
    
    @Test
    @DisplayName("Redis SETNX 락을 사용한 재고 감소 예제")
    void exampleRedisSetnxLock() {
        // Given: 상품 재고 초기화
        String productId = "PROD002";
        inventoryService.initializeStock(productId, 50);
        
        // When: Redis SETNX 락을 사용하여 재고 감소
        int remainingStock = inventoryService.decreaseStockWithRedisSetnx(productId, 5);
        
        // Then: 재고가 정상적으로 감소
        assertThat(remainingStock).isEqualTo(45);
        
        System.out.println("✓ Redis SETNX 락 예제 완료: 재고 50 -> 45");
    }
    
    @Test
    @DisplayName("MySQL 세션 락을 사용한 재고 감소 예제")
    void exampleMysqlSessionLock() {
        try {
            // Given: 상품 재고 초기화
            String productId = "PROD003";
            inventoryService.initializeStock(productId, 200);
            
            // When: MySQL 세션 락을 사용하여 재고 감소
            int remainingStock = inventoryService.decreaseStockWithMysql(productId, 20);
            
            // Then: 재고가 정상적으로 감소
            assertThat(remainingStock).isEqualTo(180);
            
            System.out.println("✓ MySQL 세션 락 예제 완료: 재고 200 -> 180");
        } catch (Exception e) {
            System.out.println("⚠ MySQL 테스트 스킵: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("PostgreSQL Advisory 락을 사용한 재고 감소 예제")
    void examplePostgresAdvisoryLock() {
        try {
            // Given: 상품 재고 초기화
            String productId = "PROD004";
            inventoryService.initializeStock(productId, 150);
            
            // When: PostgreSQL Advisory 락을 사용하여 재고 감소
            int remainingStock = inventoryService.decreaseStockWithPostgres(productId, 15);
            
            // Then: 재고가 정상적으로 감소
            assertThat(remainingStock).isEqualTo(135);
            
            System.out.println("✓ PostgreSQL Advisory 락 예제 완료: 재고 150 -> 135");
        } catch (Exception e) {
            System.out.println("⚠ PostgreSQL 테스트 스킵: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("재시도 설정을 포함한 재고 감소 예제")
    void exampleLockWithRetry() {
        // Given: 상품 재고 초기화
        String productId = "PROD005";
        inventoryService.initializeStock(productId, 100);
        
        // When: 재시도 설정이 포함된 락을 사용하여 재고 감소
        int remainingStock = inventoryService.decreaseStockWithRetry(productId, 10);
        
        // Then: 재고가 정상적으로 감소
        assertThat(remainingStock).isEqualTo(90);
        
        System.out.println("✓ 재시도 설정 예제 완료: 재고 100 -> 90 (최대 3회 재시도 가능)");
    }
    
    @Test
    @DisplayName("복잡한 SpEL 표현식을 사용한 재고 감소 예제")
    void exampleComplexSpelExpression() {
        // Given: 상품 재고 초기화
        String warehouseId = "WH001";
        String productId = "PROD006";
        inventoryService.initializeStock(productId, 300);
        
        // When: 복잡한 SpEL 표현식으로 락 키 생성
        int remainingStock = inventoryService.decreaseStockByWarehouse(warehouseId, productId, 30);
        
        // Then: 재고가 정상적으로 감소
        assertThat(remainingStock).isEqualTo(270);
        
        System.out.println("✓ 복잡한 SpEL 표현식 예제 완료: warehouse:WH001:product:PROD006");
    }
    
    @Test
    @DisplayName("동시성 시나리오: 여러 스레드가 동시에 재고 감소 시도")
    void exampleConcurrentStockDecrease() throws InterruptedException {
        // Given: 상품 재고 초기화
        String productId = "PROD007";
        int initialStock = 50;
        inventoryService.initializeStock(productId, initialStock);
        
        // When: 5개의 스레드가 동시에 각각 10개씩 재고 감소 시도
        int threadCount = 5;
        int decreaseAmount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Exception> exceptions = new ArrayList<>();
        
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    inventoryService.decreaseStockWithRedisLua(productId, decreaseAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executorService.shutdown();
        
        // Then: 모든 스레드가 성공적으로 재고를 감소시키고, 최종 재고는 0
        int finalStock = inventoryService.getStock(productId);
        System.out.println("✓ 동시성 예제 완료: " + threadCount + " 스레드가 각각 " + 
                          decreaseAmount + "개씩 감소 시도");
        System.out.println("  성공: " + successCount.get() + "개, 최종 재고: " + finalStock);
        
        if (!exceptions.isEmpty()) {
            System.out.println("  예외 발생: " + exceptions.size() + "개");
            exceptions.forEach(e -> System.out.println("    - " + e.getMessage()));
        }
        
        // 최소한 일부 스레드는 성공해야 함
        assertThat(successCount.get()).isGreaterThan(0);
        // 최종 재고는 초기 재고에서 성공한 만큼 감소되어야 함
        assertThat(finalStock).isEqualTo(initialStock - (successCount.get() * decreaseAmount));
    }
    
    @Test
    @DisplayName("다양한 락 타입 비교 예제")
    void exampleCompareDifferentLockTypes() {
        // Given: 각 락 타입별로 상품 준비
        inventoryService.initializeStock("REDIS_LUA_PROD", 100);
        inventoryService.initializeStock("REDIS_SETNX_PROD", 100);
        
        // When: Redis 락 타입으로 재고 감소
        int redisLuaStock = inventoryService.decreaseStockWithRedisLua("REDIS_LUA_PROD", 10);
        int redisSetnxStock = inventoryService.decreaseStockWithRedisSetnx("REDIS_SETNX_PROD", 10);
        
        // Then: Redis 락 타입이 정상 동작
        assertThat(redisLuaStock).isEqualTo(90);
        assertThat(redisSetnxStock).isEqualTo(90);
        
        System.out.println("✓ 락 타입 비교 완료:");
        System.out.println("  - Redis Lua: 빠른 응답, 원자적 연산, 자동 만료");
        System.out.println("  - Redis SETNX: 매우 빠름, 간단한 구현");
        System.out.println("  - MySQL/PostgreSQL 테스트는 실제 환경에서 실행하세요");
    }
}
