package com.cheetsheet.distributedlock.model;

import java.time.Instant;

/**
 * 락의 메타데이터를 저장하는 객체 (Redis 구현에서 사용)
 */
public class LockMetadata {
    
    private final String lockKey;
    private final String ownerId;
    private final Instant acquiredAt;
    private final int timeoutSeconds;
    
    public LockMetadata(String lockKey, String ownerId, Instant acquiredAt, int timeoutSeconds) {
        this.lockKey = lockKey;
        this.ownerId = ownerId;
        this.acquiredAt = acquiredAt;
        this.timeoutSeconds = timeoutSeconds;
    }
    
    public String getLockKey() {
        return lockKey;
    }
    
    public String getOwnerId() {
        return ownerId;
    }
    
    public Instant getAcquiredAt() {
        return acquiredAt;
    }
    
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
    
    @Override
    public String toString() {
        return "LockMetadata{" +
                "lockKey='" + lockKey + '\'' +
                ", ownerId='" + ownerId + '\'' +
                ", acquiredAt=" + acquiredAt +
                ", timeoutSeconds=" + timeoutSeconds +
                '}';
    }
}
