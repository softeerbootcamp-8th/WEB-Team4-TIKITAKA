package com.tikitaka.bidwinback.auction.domain.exception;

import com.tikitaka.bidwinback.global.exception.BusinessException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;

public class TradeException extends BusinessException {

    public TradeException(ErrorCode errorCode) {
        super(errorCode);
    }

    public TradeException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
