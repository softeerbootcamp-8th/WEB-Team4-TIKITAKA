package com.tikitaka.bidwinback.mypage.domain.exception;

import com.tikitaka.bidwinback.global.exception.BusinessException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;

public class MyPageException extends BusinessException {

    public MyPageException(ErrorCode errorCode) {
        super(errorCode);
    }

    public MyPageException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
