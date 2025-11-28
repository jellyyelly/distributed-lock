package com.cheetsheet.distributedlock.exception;

/**
 * 락이 이미 다른 클라이언트에 의해 보유 중일 때 발생하는 예외
 */
public class LockAlreadyHeldException extends LockAcquisitionException {
    
    public LockAlreadyHeldException(String lockKey) {
        super(String.format("Lock '%s' is already held by another client", lockKey), lockKey);
    }
}
