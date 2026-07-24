package com.tikitaka.bidwinback.member.domain.exception;

import com.tikitaka.bidwinback.global.exception.BusinessException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;

public class MemberException extends BusinessException {

    public MemberException(ErrorCode errorCode) {
        super(errorCode);
    }

    public MemberException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
