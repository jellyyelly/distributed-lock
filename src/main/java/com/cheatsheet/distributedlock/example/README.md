# InventoryService - 분산 락 사용 예제

## 개요

`InventoryService`는 `@DistributedLock` 애너테이션을 사용하여 분산 환경에서 재고 관리를 안전하게 수행하는 실용적인 예제입니다.

## 주요 기능

### 1. 다양한 락 타입 지원

#### Redis Lua Script 락
```java
@DistributedLock(
    key = "#productId",
    type = LockType.REDIS_LUA,
    timeout = 10
)
public int decreaseStockWithRedisLua(String productId, int quantity)
```

**특징:**
- 원자적 연산 보장
- 자동 만료 기능
- 빠른 응답 시간

**사용 시나리오:**
- 높은 동시성이 예상되는 경우
- 빠른 응답이 필요한 경우

#### Redis SETNX 락
```java
@DistributedLock(
    key = "'inventory:' + #productId",
    type = LockType.REDIS_SETNX,
    timeout = 5
)
public int decreaseStockWithRedisSetnx(String productId, int quantity)
```

**특징:**
- 매우 빠른 응답 시간
- 간단한 구현
- 기본 만료 시간 자동 적용

**사용 시나리오:**
- 간단한 락이 필요한 경우
- 성능이 최우선인 경우

#### MySQL 세션 락
```java
@DistributedLock(
    key = "'product:' + #productId",
    type = LockType.MYSQL_SESSION,
    timeout = 15
)
public int decreaseStockWithMysql(String productId, int quantity)
```

**특징:**
- 세션 기반 자동 해제
- 데이터베이스 트랜잭션과 통합 가능
- 중간 수준의 응답 시간

**사용 시나리오:**
- MySQL을 이미 사용 중인 경우
- 데이터베이스 트랜잭션과 함께 사용

#### PostgreSQL Advisory 락
```java
@DistributedLock(
    key = "'stock:' + #productId",
    type = LockType.POSTGRES_ADVISORY,
    timeout = 10
)
public int decreaseStockWithPostgres(String productId, int quantity)
```

**특징:**
- 애플리케이션 레벨 락
- 문자열 키를 정수 해시로 자동 변환
- 유연한 제어

**사용 시나리오:**
- PostgreSQL을 이미 사용 중인 경우
- 애플리케이션 레벨 락이 필요한 경우

### 2. SpEL 표현식을 사용한 동적 락 키 생성

#### 단순 파라미터 참조
```java
@DistributedLock(key = "#productId", ...)
```

#### 문자열 연결
```java
@DistributedLock(key = "'inventory:' + #productId", ...)
```

#### 복잡한 표현식
```java
@DistributedLock(key = "'warehouse:' + #warehouseId + ':product:' + #productId", ...)
public int decreaseStockByWarehouse(String warehouseId, String productId, int quantity)
```

### 3. 재시도 설정

락 획득에 실패했을 때 자동으로 재시도하는 기능을 제공합니다:

```java
@DistributedLock(
    key = "'product:' + #productId",
    type = LockType.REDIS_LUA,
    timeout = 10,
    retryCount = 3,        // 최대 3번 재시도
    retryInterval = 200    // 재시도 간격 200ms
)
public int decreaseStockWithRetry(String productId, int quantity)
```

**사용 시나리오:**
- 높은 경합이 예상되는 경우
- 락 획득 실패를 최소화해야 하는 경우

## 사용 방법

### 1. 서비스 주입
```java
@Service
public class OrderService {
    
    @Autowired
    private InventoryService inventoryService;
    
    public void processOrder(String productId, int quantity) {
        // 재고 감소 (자동으로 분산 락으로 보호됨)
        int remainingStock = inventoryService.decreaseStockWithRedisLua(productId, quantity);
        System.out.println("남은 재고: " + remainingStock);
    }
}
```

### 2. 재고 초기화
```java
inventoryService.initializeStock("PROD001", 100);
```

### 3. 재고 조회
```java
int currentStock = inventoryService.getStock("PROD001");
```

### 4. 재고 감소
```java
// Redis Lua 락 사용
int remaining = inventoryService.decreaseStockWithRedisLua("PROD001", 10);

// 재시도 설정 포함
int remaining = inventoryService.decreaseStockWithRetry("PROD001", 10);

// 창고별 재고 관리
int remaining = inventoryService.decreaseStockByWarehouse("WH001", "PROD001", 10);
```

## 동시성 처리

`@DistributedLock` 애너테이션은 AOP를 통해 자동으로 다음을 보장합니다:

1. **메서드 실행 전 락 획득**
2. **메서드 실행**
3. **메서드 실행 후 락 해제 (finally 블록에서 보장)**

따라서 여러 스레드나 서버에서 동시에 같은 상품의 재고를 감소시키려고 해도, 한 번에 하나의 요청만 처리됩니다.

## 에러 처리

### 재고 부족
```java
try {
    inventoryService.decreaseStockWithRedisLua("PROD001", 1000);
} catch (IllegalStateException e) {
    // "Insufficient stock for product PROD001: requested=1000, available=100"
    System.err.println(e.getMessage());
}
```

### 락 획득 실패
```java
try {
    inventoryService.decreaseStockWithRedisLua("PROD001", 10);
} catch (LockAcquisitionException e) {
    // "Failed to acquire lock after retries: PROD001"
    System.err.println(e.getMessage());
}
```

## 성능 비교

| 락 타입 | 응답 시간 | 처리량 | 자동 만료 | 소유자 검증 |
|---------|----------|--------|-----------|------------|
| Redis Lua | 빠름 | 높음 | ✓ | ✓ |
| Redis SETNX | 매우 빠름 | 매우 높음 | ✓ | ✗ |
| MySQL Session | 중간 | 중간 | ✓ | ✗ |
| PostgreSQL Advisory | 중간 | 중간 | ✗ | ✗ |

## 테스트 예제

`InventoryServiceExampleTest` 클래스에서 다양한 사용 예제를 확인할 수 있습니다:

- 각 락 타입별 기본 사용법
- 동시성 시나리오 테스트
- 복잡한 SpEL 표현식 사용
- 재시도 설정 사용

## 주의사항

1. **락 타임아웃 설정**: 적절한 타임아웃 값을 설정하여 데드락을 방지하세요.
2. **재시도 설정**: 과도한 재시도는 시스템 부하를 증가시킬 수 있습니다.
3. **락 키 설계**: 락 키는 충분히 구체적이어야 하며, 불필요한 경합을 피해야 합니다.
4. **예외 처리**: 락 획득 실패와 비즈니스 로직 실패를 구분하여 처리하세요.

## 확장 가능성

이 예제를 기반으로 다음과 같은 기능을 추가할 수 있습니다:

- 재고 증가 기능
- 재고 예약 기능
- 배치 재고 감소
- 재고 이력 추적
- 재고 알림 기능
