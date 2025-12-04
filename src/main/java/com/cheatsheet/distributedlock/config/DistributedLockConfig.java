package com.cheatsheet.distributedlock.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 분산 락 AOP 설정
 */
@Configuration
@EnableAspectJAutoProxy(exposeProxy = true) // self-invocation을 위해 프록시 노출
@EnableRetry // Spring Retry 활성화
public class DistributedLockConfig {
    // DistributedLockAspect는 @Component로 자동 등록되므로 별도 Bean 정의 불필요
}
