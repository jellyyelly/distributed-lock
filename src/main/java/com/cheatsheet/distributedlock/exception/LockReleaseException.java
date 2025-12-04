package com.cheatsheet.distributedlock.exception;

/**
 * 락 해제 실패 시 발생하는 예외
 */
public class LockReleaseException extends LockException {
    
    public LockReleaseException(String message, String lockKey) {
        super(message, lockKey, LockOperation.RELEASE);
    }
    
    public LockReleaseException(String message, String lockKey, Throwable cause) {
        super(message, lockKey, LockOperation.RELEASE, cause);
    }
}
