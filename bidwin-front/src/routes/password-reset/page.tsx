import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { KeyRound } from 'lucide-react'
import EmailSentCard from '../../components/auth/EmailSentCard'
import type { ResendResult } from '../../components/auth/EmailSentCard'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import TextInput from '../../components/ui/TextInput'
import { requestPasswordReset } from '../../lib/api/auth'
import { validateEmail } from '../../lib/auth/validation'

function PasswordResetRequestPage() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [sentEmail, setSentEmail] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function sendResetLink(targetEmail: string): Promise<string | null> {
    const result = await requestPasswordReset(targetEmail)
    return result.ok ? null : result.message
  }

  /* 재설정 메일은 발송 여부를 따로 알려주지 않으므로 요청 성공을 발송으로 본다. */
  async function resendResetLink(targetEmail: string): Promise<ResendResult> {
    const error = await sendResetLink(targetEmail)
    return error ? { sent: false, error } : { sent: true }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (isSubmitting) return
    const normalizedEmail = email.trim()
    const validationError = validateEmail(normalizedEmail)
    if (validationError) {
      setError(validationError)
      return
    }

    setError('')
    setIsSubmitting(true)
    const requestError = await sendResetLink(normalizedEmail)
    setIsSubmitting(false)
    if (requestError) {
      setError(requestError)
      return
    }
    setSentEmail(normalizedEmail)
  }

  return (
    <main className="flex min-h-[calc(100dvh-4rem)] items-center justify-center px-lg">
      {sentEmail ? (
        <EmailSentCard
          title="이메일을 확인해주세요"
          description={
            <>
              <span className="font-semibold text-ink">{sentEmail}</span>
              {' 주소로'}
              <br />
              비밀번호 재설정 링크를 보내드렸어요.
            </>
          }
          resendLabel="재설정 메일 재전송"
          resendToastMessage="재설정 메일을 다시 보냈어요."
          onResend={() => resendResetLink(sentEmail)}
          footer={<LoginLink />}
        />
      ) : (
        <Card className="flex w-full max-w-[420px] flex-col items-center gap-lg py-xxl shadow-soft">
          <span className="flex h-16 w-16 items-center justify-center rounded-full bg-primary-tint">
            <KeyRound size={28} className="text-primary" strokeWidth={2} />
          </span>
          <div className="flex flex-col items-center gap-xs text-center">
            <h1 className="text-2xl font-bold text-ink">비밀번호 찾기</h1>
            <p className="text-base leading-relaxed text-body">
              가입하신 이메일 주소를 입력하시면
              <br />
              비밀번호 재설정 링크를 보내드려요.
            </p>
          </div>
          <form onSubmit={handleSubmit} className="flex w-full flex-col gap-lg">
            <TextInput
              label="이메일"
              type="email"
              placeholder="you@example.com"
              value={email}
              onChange={(event) => {
                setEmail(event.target.value)
                setError('')
              }}
              error={error}
              autoFocus
            />
            <Button type="submit" size="lg" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? '전송 중…' : '재설정 링크 보내기'}
            </Button>
          </form>
          <LoginLink />
        </Card>
      )}
    </main>
  )
}

function LoginLink() {
  return (
    <Link to="/login" className="text-sm font-medium text-muted hover:text-body">
      로그인 화면으로 돌아가기
    </Link>
  )
}

export default PasswordResetRequestPage
