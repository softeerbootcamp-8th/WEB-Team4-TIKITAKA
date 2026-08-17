import { useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { requestEmailVerification } from '../lib/api/auth'
import { EMAIL_VERIFICATION_ROUTE } from '../lib/auth/emailVerification'
import type { EmailVerificationLocationState } from '../lib/auth/emailVerification'

/*
 * 이메일 인증이 끝나지 않은 계정을 인증 안내 화면으로 넘긴다.
 * 재전송 제한에 걸리지 않았으면 인증 메일도 함께 다시 보내고,
 * 걸렸으면 남은 대기 시간을 넘겨 재전송 버튼이 그만큼 잠기게 한다.
 */
export function useEmailVerificationRedirect() {
  const navigate = useNavigate()

  return useCallback(
    async (email: string) => {
      const result = await requestEmailVerification(email)
      const state: EmailVerificationLocationState = result.ok
        ? {
            email,
            sent: result.data.sent,
            retryAfterSeconds: result.data.retryAfterSeconds,
          }
        : { email, sendError: result.message }

      navigate(EMAIL_VERIFICATION_ROUTE, { state })
    },
    [navigate],
  )
}
