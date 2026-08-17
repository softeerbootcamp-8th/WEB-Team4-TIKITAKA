import { getJson, postJson } from './client'
import type { ApiResult } from './client'

const AUTH_API_PATH = {
  login: '/api/v1/auth/login',
  logout: '/api/v1/auth/logout',
  session: '/api/v1/auth/session',
  signUp: '/api/v1/auth/signups',
  emailAvailability: '/api/v1/auth/signups/email/verify',
  nicknameAvailability: '/api/v1/auth/signups/nickname/verify',
  emailVerificationSend: '/api/v1/auth/signups/email/send',
  emailVerificationConfirm: '/api/v1/auth/signups/email/confirm',
  passwordReset: '/api/v1/auth/password-resets',
  passwordResetConfirm: '/api/v1/auth/password-resets/confirm',
}

/* 화면 분기가 필요한 백엔드 ErrorCode */
const AUTH_ERROR_CODE = {
  /* 이메일 인증이 끝나지 않은 계정 (ErrorCode.EMAIL_VERIFICATION_PENDING) */
  emailVerificationPending: 'MEMBER_409_3',
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

interface AvailabilityResponse {
  available: boolean
}

/* 백엔드 EmailVerificationSendResponse */
interface EmailVerificationSendResponse {
  /* 메일이 실제로 나갔는지. 재전송 제한에 걸리면 false */
  sent: boolean
  /* 다음 재전송이 가능해질 때까지 남은 시간(초) */
  retryAfterSeconds: number
}

interface PasswordResetConfirmRequest {
  token: string
  newPassword: string
  newPasswordConfirm: string
}

function requestEmailAvailability(email: string): Promise<ApiResult<AvailabilityResponse>> {
  return postJson<AvailabilityResponse, { email: string }>(
    AUTH_API_PATH.emailAvailability,
    { email },
  )
}

function requestNicknameAvailability(
  nickname: string,
): Promise<ApiResult<AvailabilityResponse>> {
  return postJson<AvailabilityResponse, { nickname: string }>(
    AUTH_API_PATH.nicknameAvailability,
    { nickname },
  )
}

function requestSignUp(request: SignUpRequest): Promise<ApiResult<SignUpResponse>> {
  return postJson<SignUpResponse, SignUpRequest>(AUTH_API_PATH.signUp, request)
}

function requestEmailVerification(
  email: string,
): Promise<ApiResult<EmailVerificationSendResponse>> {
  return postJson<EmailVerificationSendResponse, { email: string }>(
    AUTH_API_PATH.emailVerificationSend,
    { email },
  )
}

function requestEmailVerificationConfirm(token: string): Promise<ApiResult<void>> {
  return postJson<void, { token: string }>(AUTH_API_PATH.emailVerificationConfirm, { token })
}

function requestPasswordReset(email: string): Promise<ApiResult<void>> {
  return postJson<void, { email: string }>(AUTH_API_PATH.passwordReset, { email })
}

function requestPasswordResetConfirm(
  request: PasswordResetConfirmRequest,
): Promise<ApiResult<void>> {
  return postJson<void, PasswordResetConfirmRequest>(
    AUTH_API_PATH.passwordResetConfirm,
    request,
  )
}

function requestLogin(request: LoginRequest): Promise<ApiResult<void>> {
  return postJson<void, LoginRequest>(AUTH_API_PATH.login, request)
}

function requestLogout(): Promise<ApiResult<void>> {
  return postJson<void, Record<string, never>>(AUTH_API_PATH.logout, {})
}

function requestSession(): Promise<ApiResult<void>> {
  return getJson<void>(AUTH_API_PATH.session)
}

export {
  AUTH_ERROR_CODE,
  requestEmailAvailability,
  requestEmailVerification,
  requestEmailVerificationConfirm,
  requestLogin,
  requestLogout,
  requestNicknameAvailability,
  requestPasswordReset,
  requestPasswordResetConfirm,
  requestSession,
  requestSignUp,
}
export type {
  AvailabilityResponse,
  EmailVerificationSendResponse,
  LoginRequest,
  PasswordResetConfirmRequest,
  SignUpRequest,
  SignUpResponse,
}
