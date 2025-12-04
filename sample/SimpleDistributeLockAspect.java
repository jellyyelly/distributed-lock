package com.inet.api.common.lock;

import static com.inet.api.common.log.LogMarker.ERROR_MARKER;
import static com.inet.api.common.log.LogMarker.INFO_MARKER;

import java.lang.reflect.Method;
import java.time.Duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import com.inet.api.exception.BusinessException;
import com.inet.api.exception.ErrorCode;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class SimpleDistributeLockAspect {

    private final LuaScriptLockManager luaScriptLockManager;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(distributeLock)")
    public Object acquireLockWithAutoRelease(ProceedingJoinPoint joinPoint, DistributeLock distributeLock) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // SpEL 표현식으로 lockKey 생성
        String lockKey = generateLockKey(joinPoint, method, distributeLock.key());

        // TTL 문자열을 Duration으로 파싱
        Duration ttl = parseDuration(distributeLock.ttl());

        log.info(INFO_MARKER, "분산 락 획득 시도: lockKey={}, ttl={}", lockKey, ttl);

        // 분산 락 획득
        SimpleDistributedLock lock = luaScriptLockManager.tryLockBy(lockKey, ttl);

        if (lock == null) {
            log.error(ERROR_MARKER, "분산 락 획득 실패: lockKey={}", lockKey);
            throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAIL, "동시 요청이 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            log.info(INFO_MARKER, "분산 락 획득 성공: lockKey={}", lockKey);
            return joinPoint.proceed();
        } finally {
            boolean released = luaScriptLockManager.releaseLock(lock);
            if (released) {
                log.info(INFO_MARKER, "분산 락 해제 성공: lockKey={}", lockKey);
            } else {
                log.error(ERROR_MARKER, "분산 락 해제 실패: lockKey={}", lockKey);
            }
        }
    }

    /**
     * SpEL 표현식을 평가하여 lockKey 생성
     */
    private String generateLockKey(ProceedingJoinPoint joinPoint, Method method, String keyExpression) {
        if (keyExpression == null || keyExpression.isEmpty()) {
            // 기본 lockKey 생성
            String className = method.getDeclaringClass().getSimpleName();
            String methodName = method.getName();
            return String.format("lock:%s:%s", className, methodName);
        }

        // SpEL 컨텍스트 생성
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 메서드 파라미터를 SpEL 컨텍스트에 바인딩
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        // SpEL 표현식 평가
        String evaluatedKey = parser.parseExpression(keyExpression).getValue(context, String.class);

        // prefix 추가
        return "lock:" + evaluatedKey;
    }

    /**
     * 문자열을 Duration으로 파싱
     * 예: "30s" -> 30초, "5m" -> 5분, "PT30S" -> 30초 (ISO-8601)
     */
    private Duration parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) {
            return Duration.ofSeconds(30); // 기본값 30초
        }

        try {
            // ISO-8601 형식 시도 (예: PT30S)
            if (durationStr.startsWith("PT") || durationStr.startsWith("P")) {
                return Duration.parse(durationStr);
            }

            // 간단한 형식 파싱 (예: 30s, 5m)
            String numPart = durationStr.replaceAll("[^0-9]", "");
            String unitPart = durationStr.replaceAll("[0-9]", "").toLowerCase();

            long value = Long.parseLong(numPart);

            return switch (unitPart) {
                case "s" -> Duration.ofSeconds(value);
                case "m" -> Duration.ofMinutes(value);
                case "h" -> Duration.ofHours(value);
                default -> Duration.ofSeconds(value);
            };
        } catch (Exception e) {
            log.error(ERROR_MARKER, "Duration 파싱 실패: {}, 기본값 30초 사용", durationStr, e);
            return Duration.ofSeconds(30);
        }
    }
}
