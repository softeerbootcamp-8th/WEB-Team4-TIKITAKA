import { postJson } from './client'
import type { ApiResult } from './client'

const AUTH_API_PATH = {
  signUp: '/api/v1/auth/signups',
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

export { requestSignUp }
export type { SignUpRequest, SignUpResponse }
