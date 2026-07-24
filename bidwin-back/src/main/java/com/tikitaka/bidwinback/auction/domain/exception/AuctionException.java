package com.tikitaka.bidwinback.auction.domain.exception;

import com.tikitaka.bidwinback.global.exception.BusinessException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;

public class AuctionException extends BusinessException {

    public AuctionException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuctionException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
