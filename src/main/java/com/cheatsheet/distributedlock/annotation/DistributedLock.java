package com.cheatsheet.distributedlock.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.cheatsheet.distributedlock.enums.LockType;

/**
 * 메서드에 선언하여 분산 락을 적용하는 커스텀 애너테이션
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    
    /**
     * 락 키 (SpEL 표현식 지원)
     * 예: "product:#productId", "'order:' + #orderId"
     */
    String key();
    
    /**
     * 락 타입 (MySQL, PostgreSQL, Redis 등)
     */
    LockType type() default LockType.REDIS_LUA;
    
    /**
     * 타임아웃 (초)
     */
    int timeout() default 10;
    
    /**
     * 락 획득 실패 시 재시도 횟수
     */
    int retryCount() default 0;
    
    /**
     * 재시도 간격 (밀리초)
     */
    long retryInterval() default 100;
}
