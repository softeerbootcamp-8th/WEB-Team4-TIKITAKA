import { getJson, postJson } from './client'
import type { ApiResult } from './client'

const AUTH_API_PATH = {
  login: '/api/v1/auth/login',
  session: '/api/v1/auth/session',
  signUp: '/api/v1/auth/signups',
}

interface LoginRequest {
  email: string
  password: string
}

/* 백엔드 SignUpRequest와 필드 이름·형식이 1:1로 대응한다(전화번호는 하이픈 없는 숫자). */
interface SignUpRequest {
  email: string
  password: string
  name: string
  phoneNumber: string
  nickname: string
}

/* 백엔드 SignUpResponse */
interface SignUpResponse {
  memberId: number
  email: string
  nickname: string
}

function requestSignUp(request: SignUpRequest): Promise<ApiResult<SignUpResponse>> {
  return postJson<SignUpResponse, SignUpRequest>(AUTH_API_PATH.signUp, request)
}

function requestLogin(request: LoginRequest): Promise<ApiResult<void>> {
  return postJson<void, LoginRequest>(AUTH_API_PATH.login, request)
}

function requestSession(): Promise<ApiResult<void>> {
  return getJson<void>(AUTH_API_PATH.session)
}

export { requestLogin, requestSession, requestSignUp }
export type { LoginRequest, SignUpRequest, SignUpResponse }
