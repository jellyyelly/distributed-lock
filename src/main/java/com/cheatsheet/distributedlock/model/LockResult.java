package com.cheatsheet.distributedlock.model;

import java.time.Instant;

/**
 * 락 작업의 결과를 나타내는 값 객체
 */
public class LockResult {
    
    private final boolean success;
    private final String lockKey;
    private final String message;
    private final Instant timestamp;
    
    public LockResult(boolean success, String lockKey, String message) {
        this.success = success;
        this.lockKey = lockKey;
        this.message = message;
        this.timestamp = Instant.now();
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getLockKey() {
        return lockKey;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return "LockResult{" +
                "success=" + success +
                ", lockKey='" + lockKey + '\'' +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
