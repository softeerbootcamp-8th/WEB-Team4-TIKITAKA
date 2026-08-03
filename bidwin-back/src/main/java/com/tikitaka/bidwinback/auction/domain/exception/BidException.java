package com.tikitaka.bidwinback.auction.domain.exception;

import com.tikitaka.bidwinback.global.exception.BusinessException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;

public class BidException extends BusinessException {

    public BidException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BidException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
