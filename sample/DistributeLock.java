public @interface DistributeLock {

    /**
     * SpEL 표현식을 사용한 락 키
     * 예: "#examSessionId + '_' + #request.userId"
     */
    String key() default "";

    /**
     * 락 유지 시간 (ISO-8601 duration 형식)
     * 예: "30s", "5m", "PT30S"
     */
    String ttl() default "30s";
}
