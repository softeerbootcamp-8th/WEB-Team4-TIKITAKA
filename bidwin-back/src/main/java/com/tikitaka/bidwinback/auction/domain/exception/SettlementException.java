package com.tikitaka.bidwinback.auction.domain.exception;

import com.tikitaka.bidwinback.global.exception.BusinessException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;

public class SettlementException extends BusinessException {

    public SettlementException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SettlementException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
