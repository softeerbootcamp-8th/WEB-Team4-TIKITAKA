package com.tikitaka.bidwinback.global.auth;

import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(Login.class)
                && parameter.getParameterType() == AuthMember.class;
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Object attribute = webRequest.getAttribute(
                AuthConstant.REQUEST_ATTRIBUTE_KEY,
                RequestAttributes.SCOPE_REQUEST
        );

        if (attribute instanceof AuthMember authMember) {
            return authMember;
        }

        throw new AuthException(ErrorCode.UNAUTHENTICATED);
    }
}
