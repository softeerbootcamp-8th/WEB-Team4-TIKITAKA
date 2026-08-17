/*
 * 이메일 인증이 끝나지 않은(PENDING) 계정을 인증 안내 화면으로 넘길 때 쓰는 공통 규약.
 * 로그인·회원가입 화면이 같은 형태의 라우팅 상태를 넘겨준다.
 */

const EMAIL_VERIFICATION_ROUTE = '/email-verification'

/* 서버가 남은 대기 시간을 알려주지 못했을 때 쓰는 재전송 간격 */
const DEFAULT_RESEND_COOLDOWN_SECONDS = 60

interface EmailVerificationLocationState {
  email?: string
  /* 화면에 들어오기 직전에 인증 메일이 실제로 나갔는지. 재전송 제한에 걸리면 false */
  sent?: boolean
  /* 다음 재전송이 가능해질 때까지 남은 시간(초) */
  retryAfterSeconds?: number
  /* 발송 요청 자체가 실패했을 때의 안내 메시지 */
  sendError?: string
  /* 토큰 인증까지 마친 상태로 들어왔는지 */
  verified?: boolean
}

export { DEFAULT_RESEND_COOLDOWN_SECONDS, EMAIL_VERIFICATION_ROUTE }
export type { EmailVerificationLocationState }
