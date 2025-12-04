package com.cheatsheet.distributedlock.aspect;

import com.cheatsheet.distributedlock.annotation.DistributedLock;
import com.cheatsheet.distributedlock.enums.LockType;
import com.cheatsheet.distributedlock.exception.LockAcquisitionException;
import com.cheatsheet.distributedlock.service.DistributedLockService;
import com.cheatsheet.distributedlock.service.LockRetryService;
import com.cheatsheet.distributedlock.util.SpelKeyResolver;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @DistributedLock 애너테이션이 붙은 메서드를 가로채는 AOP Aspect
 */
@Aspect
@Component
@Slf4j
public class DistributedLockAspect {
    
    private final Map<LockType, DistributedLockService> lockServices;
    private final LockRetryService lockRetryService;
    
    /**
     * 생성자: 모든 DistributedLockService 구현체를 주입받아 LockType별로 매핑
     * 
     * @param lockServiceList 모든 DistributedLockService 구현체 리스트
     * @param lockRetryService 락 재시도 서비스
     */
    public DistributedLockAspect(List<DistributedLockService> lockServiceList, LockRetryService lockRetryService) {
        this.lockServices = lockServiceList.stream()
                .collect(Collectors.toMap(
                        DistributedLockService::getSupportedType,
                        Function.identity()
                ));
        this.lockRetryService = lockRetryService;
        log.info("DistributedLockAspect initialized with {} lock services", lockServices.size());
    }
    
    /**
     * @DistributedLock 애너테이션이 붙은 메서드를 가로채는 Around Advice
     * 
     * @param joinPoint 메서드 실행 지점
     * @param distributedLock 애너테이션 인스턴스
     * @return 메서드 실행 결과
     * @throws Throwable 메서드 실행 중 발생한 예외
     */
    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        // 1. SpEL을 사용하여 동적 락 키 생성
        String lockKey = resolveLockKey(distributedLock.key(), joinPoint);
        log.debug("Resolved lock key: {}", lockKey);
        
        // 2. 락 타입에 따라 적절한 서비스 선택
        DistributedLockService lockService = selectLockService(distributedLock.type());
        
        // 3. 재시도 설정이 있는 경우 @Retryable을 통한 재시도, 없는 경우 직접 획득
        boolean acquired;
        if (distributedLock.retryCount() > 0) {
            // @Retryable을 통한 재시도 (LockRetryService 사용)
            acquired = lockRetryService.acquireLockWithRetry(lockService, lockKey, distributedLock.timeout());
        } else {
            // 재시도 없이 단일 시도
            acquired = lockService.acquireLock(lockKey, distributedLock.timeout());
        }
        
        if (!acquired) {
            throw new LockAcquisitionException(
                    "Failed to acquire lock after retries: " + lockKey, 
                    lockKey
            );
        }
        
        log.debug("Lock acquired: {}", lockKey);
        
        try {
            // 4. 원본 메서드 실행
            return joinPoint.proceed();
        } finally {
            // 5. 락 해제 (반드시 실행)
            boolean released = lockService.releaseLock(lockKey);
            if (released) {
                log.debug("Lock released: {}", lockKey);
            } else {
                log.warn("Failed to release lock: {}", lockKey);
            }
        }
    }
    
    /**
     * SpEL 표현식을 평가하여 동적 락 키를 생성합니다.
     * 
     * @param keyExpression SpEL 표현식
     * @param joinPoint 메서드 실행 지점
     * @return 평가된 락 키
     */
    private String resolveLockKey(String keyExpression, ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        
        return SpelKeyResolver.resolve(keyExpression, method, args);
    }
    
    /**
     * 락 타입에 따라 적절한 Lock Service를 선택합니다.
     * 
     * @param lockType 락 타입
     * @return 해당 타입의 Lock Service
     * @throws IllegalArgumentException 지원하지 않는 락 타입인 경우
     */
    private DistributedLockService selectLockService(LockType lockType) {
        DistributedLockService service = lockServices.get(lockType);
        if (service == null) {
            throw new IllegalArgumentException("Unsupported lock type: " + lockType);
        }
        return service;
    }
    
}
