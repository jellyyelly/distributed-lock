package com.cheatsheet.distributedlock.example;

import com.cheatsheet.distributedlock.annotation.DistributedLock;
import com.cheatsheet.distributedlock.enums.LockType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 재고 관리 서비스 - 분산 락 사용 예제
 * 
 * 이 서비스는 @DistributedLock 애너테이션을 사용하여 
 * 동시성 제어가 필요한 재고 감소 시나리오를 구현합니다.
 * 
 * 다양한 락 타입(MySQL, PostgreSQL, Redis)과 
 * SpEL 표현식을 사용한 동적 락 키 생성 예제를 제공합니다.
 */
@Service
@Slf4j
public class InventoryService {
    
    /**
     * 상품별 재고를 저장하는 메모리 저장소
     * 실제 환경에서는 데이터베이스를 사용합니다.
     */
    private final Map<String, Integer> inventory = new ConcurrentHashMap<>();
    
    /**
     * 초기 재고를 설정합니다.
     * 
     * @param productId 상품 ID
     * @param quantity 초기 재고 수량
     */
    public void initializeStock(String productId, int quantity) {
        inventory.put(productId, quantity);
        log.info("Initialized stock for product {}: {} units", productId, quantity);
    }
    
    /**
     * 현재 재고를 조회합니다.
     * 
     * @param productId 상품 ID
     * @return 현재 재고 수량
     */
    public int getStock(String productId) {
        return inventory.getOrDefault(productId, 0);
    }
    
    /**
     * Redis Lua Script 락을 사용한 재고 감소
     * 
     * SpEL 표현식 "#productId"를 사용하여 메서드 파라미터 기반의 동적 락 키를 생성합니다.
     * Redis Lua Script 방식은 원자적 연산을 보장하며, 자동 만료 기능을 제공합니다.
     * 
     * 성능 특성:
     * - 빠른 응답 시간 (메모리 기반)
     * - 높은 처리량
     * - 네트워크 지연에 민감
     * 
     * 사용 시나리오:
     * - 높은 동시성이 예상되는 경우
     * - 빠른 응답이 필요한 경우
     * - 자동 만료가 필요한 경우
     * 
     * @param productId 상품 ID
     * @param quantity 감소할 수량
     * @return 감소 후 남은 재고
     */
    @DistributedLock(
        key = "#productId",
        type = LockType.REDIS_LUA,
        timeout = 10
    )
    public int decreaseStockWithRedisLua(String productId, int quantity) {
        log.info("[Redis Lua] Decreasing stock for product {}: {} units", productId, quantity);
        return decreaseStockInternal(productId, quantity);
    }
    
    /**
     * Redis SETNX 락을 사용한 재고 감소
     * 
     * SpEL 표현식을 사용하여 "inventory:" 접두사가 붙은 락 키를 생성합니다.
     * Redis SETNX 방식은 간단한 구현으로 빠른 성능을 제공합니다.
     * 
     * 성능 특성:
     * - 매우 빠른 응답 시간
     * - 간단한 구현
     * - 소유자 검증 없음 (주의 필요)
     * 
     * 사용 시나리오:
     * - 간단한 락이 필요한 경우
     * - 성능이 최우선인 경우
     * - 락 소유자 검증이 불필요한 경우
     * 
     * @param productId 상품 ID
     * @param quantity 감소할 수량
     * @return 감소 후 남은 재고
     */
    @DistributedLock(
        key = "'inventory:' + #productId",
        type = LockType.REDIS_SETNX,
        timeout = 5
    )
    public int decreaseStockWithRedisSetnx(String productId, int quantity) {
        log.info("[Redis SETNX] Decreasing stock for product {}: {} units", productId, quantity);
        return decreaseStockInternal(productId, quantity);
    }
    
    /**
     * MySQL 세션 락을 사용한 재고 감소
     * 
     * MySQL GET_LOCK/RELEASE_LOCK을 사용하며, 세션이 종료되면 자동으로 락이 해제됩니다.
     * 
     * 성능 특성:
     * - 중간 수준의 응답 시간
     * - 데이터베이스 연결 필요
     * - 세션 기반 자동 해제
     * 
     * 사용 시나리오:
     * - 데이터베이스 트랜잭션과 함께 사용
     * - 세션 기반 락이 필요한 경우
     * - MySQL을 이미 사용 중인 경우
     * 
     * @param productId 상품 ID
     * @param quantity 감소할 수량
     * @return 감소 후 남은 재고
     */
    @DistributedLock(
        key = "'product:' + #productId",
        type = LockType.MYSQL_SESSION,
        timeout = 15
    )
    public int decreaseStockWithMysql(String productId, int quantity) {
        log.info("[MySQL Session] Decreasing stock for product {}: {} units", productId, quantity);
        return decreaseStockInternal(productId, quantity);
    }
    
    /**
     * PostgreSQL Advisory Lock을 사용한 재고 감소
     * 
     * PostgreSQL의 Advisory Lock은 애플리케이션 레벨의 락으로,
     * 문자열 키를 정수 해시로 변환하여 사용합니다.
     * 
     * 성능 특성:
     * - 중간 수준의 응답 시간
     * - 데이터베이스 연결 필요
     * - 트랜잭션 레벨 또는 세션 레벨 선택 가능
     * 
     * 사용 시나리오:
     * - PostgreSQL을 이미 사용 중인 경우
     * - 애플리케이션 레벨 락이 필요한 경우
     * - 데이터베이스 트랜잭션과 함께 사용
     * 
     * @param productId 상품 ID
     * @param quantity 감소할 수량
     * @return 감소 후 남은 재고
     */
    @DistributedLock(
        key = "'stock:' + #productId",
        type = LockType.POSTGRES_ADVISORY,
        timeout = 10
    )
    public int decreaseStockWithPostgres(String productId, int quantity) {
        log.info("[PostgreSQL Advisory] Decreasing stock for product {}: {} units", productId, quantity);
        return decreaseStockInternal(productId, quantity);
    }
    
    /**
     * 재시도 설정을 포함한 재고 감소
     * 
     * 락 획득에 실패하면 최대 3번까지 재시도하며, 각 재시도 사이에 200ms 대기합니다.
     * 이는 높은 경합 상황에서 락 획득 성공률을 높이는 데 유용합니다.
     * 
     * 재시도 전략:
     * - retryCount: 최대 재시도 횟수
     * - retryInterval: 재시도 간격 (밀리초)
     * 
     * 사용 시나리오:
     * - 높은 경합이 예상되는 경우
     * - 락 획득 실패를 최소화해야 하는 경우
     * - 일시적인 락 충돌을 극복해야 하는 경우
     * 
     * @param productId 상품 ID
     * @param quantity 감소할 수량
     * @return 감소 후 남은 재고
     */
    @DistributedLock(
        key = "'product:' + #productId",
        type = LockType.REDIS_LUA,
        timeout = 10,
        retryCount = 3,
        retryInterval = 200
    )
    public int decreaseStockWithRetry(String productId, int quantity) {
        log.info("[With Retry] Decreasing stock for product {}: {} units", productId, quantity);
        return decreaseStockInternal(productId, quantity);
    }
    
    /**
     * 복잡한 SpEL 표현식을 사용한 재고 감소
     * 
     * 여러 파라미터를 조합하여 동적 락 키를 생성하는 예제입니다.
     * 예: "warehouse:WH001:product:PROD123"
     * 
     * SpEL 표현식 활용:
     * - 문자열 연결: 'prefix:' + #param
     * - 메서드 호출: #param.toUpperCase()
     * - 조건 표현식: #param != null ? #param : 'default'
     * 
     * @param warehouseId 창고 ID
     * @param productId 상품 ID
     * @param quantity 감소할 수량
     * @return 감소 후 남은 재고
     */
    @DistributedLock(
        key = "'warehouse:' + #warehouseId + ':product:' + #productId",
        type = LockType.REDIS_LUA,
        timeout = 10
    )
    public int decreaseStockByWarehouse(String warehouseId, String productId, int quantity) {
        log.info("[Warehouse {}] Decreasing stock for product {}: {} units", 
                 warehouseId, productId, quantity);
        return decreaseStockInternal(productId, quantity);
    }
    
    /**
     * 실제 재고 감소 로직 (내부 메서드)
     * 
     * 이 메서드는 @DistributedLock으로 보호되는 메서드들에서 호출되며,
     * 실제 재고 감소 비즈니스 로직을 수행합니다.
     * 
     * 비즈니스 규칙:
     * - 재고가 부족하면 IllegalStateException 발생
     * - 재고 감소 후 로그 기록
     * 
     * @param productId 상품 ID
     * @param quantity 감소할 수량
     * @return 감소 후 남은 재고
     * @throws IllegalStateException 재고가 부족한 경우
     */
    private int decreaseStockInternal(String productId, int quantity) {
        int currentStock = inventory.getOrDefault(productId, 0);
        
        if (currentStock < quantity) {
            log.error("Insufficient stock for product {}: requested={}, available={}", 
                     productId, quantity, currentStock);
            throw new IllegalStateException(
                String.format("Insufficient stock for product %s: requested=%d, available=%d",
                             productId, quantity, currentStock)
            );
        }
        
        int newStock = currentStock - quantity;
        inventory.put(productId, newStock);
        
        log.info("Stock decreased for product {}: {} -> {} (decreased by {})", 
                 productId, currentStock, newStock, quantity);
        
        return newStock;
    }
    
    /**
     * 모든 재고를 초기화합니다 (테스트용)
     */
    public void clearAllStock() {
        inventory.clear();
        log.info("All stock cleared");
    }
}
