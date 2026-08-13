import { useState } from 'react'
import type { ReactNode } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { CheckCircle2, MailCheck, ShieldAlert } from 'lucide-react'
import EmailSentCard from '../../components/auth/EmailSentCard'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import {
  requestEmailVerification,
  requestEmailVerificationBypass,
  requestEmailVerificationConfirm,
} from '../../lib/api/auth'

const ROUTE = {
  login: '/login',
  emailVerification: '/email-verification',
}

interface EmailVerificationLocationState {
  email?: string
  initialSendError?: string
  verified?: boolean
}

function EmailVerificationPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const state = location.state as EmailVerificationLocationState | null
  const email = state?.email
  const token = searchParams.get('token')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isBypassing, setIsBypassing] = useState(false)
  const [isComplete, setIsComplete] = useState(state?.verified === true)
  const [sendError, setSendError] = useState(state?.initialSendError)
  const [error, setError] = useState<string | null>(null)

  async function resendVerification() {
    if (!email) return '이메일 정보가 없어 인증 메일을 다시 보낼 수 없어요.'
    const result = await requestEmailVerification(email)
    if (!result.ok) return result.message
    setSendError(undefined)
    return null
  }

  async function confirmVerification() {
    if (!token || isSubmitting) return
    setError(null)
    setIsSubmitting(true)
    const result = await requestEmailVerificationConfirm(token)
    setIsSubmitting(false)

    if (!result.ok) {
      setError(result.message)
      return
    }

    setIsComplete(true)
    navigate(ROUTE.emailVerification, { replace: true, state: { verified: true } })
  }

  async function bypassVerification() {
    if (!email || isBypassing) return
    setError(null)
    setIsBypassing(true)
    const result = await requestEmailVerificationBypass(email)
    setIsBypassing(false)

    if (!result.ok) {
      setError(result.message)
      return
    }

    setIsComplete(true)
    navigate(ROUTE.emailVerification, { replace: true, state: { verified: true } })
  }

  if (isComplete) {
    return (
      <VerificationStatusCard
        icon={
          <span className="flex h-16 w-16 items-center justify-center rounded-full bg-up-tint">
            <CheckCircle2 size={28} className="text-up" strokeWidth={2} />
          </span>
        }
        title="이메일 인증이 완료됐어요"
        description="이제 가입한 계정으로 로그인할 수 있어요."
      >
        <Button size="lg" className="min-w-[220px]" onClick={() => navigate(ROUTE.login)}>
          로그인하러 가기
        </Button>
      </VerificationStatusCard>
    )
  }

  if (token) {
    return (
      <VerificationStatusCard
        icon={
          <span className="flex h-16 w-16 items-center justify-center rounded-full bg-primary-tint">
            <MailCheck size={28} className="text-primary" strokeWidth={2} />
          </span>
        }
        title="이메일 인증을 완료해주세요"
        description="아래 버튼을 누르면 이메일 인증이 완료됩니다."
      >
        <div className="flex flex-col items-center gap-sm">
          <Button
            size="lg"
            className="min-w-[220px]"
            onClick={confirmVerification}
            disabled={isSubmitting}
          >
            {isSubmitting ? '인증 중…' : '이메일 인증 완료하기'}
          </Button>
          {error && (
            <p role="alert" className="text-sm text-down">
              {error}
            </p>
          )}
          <button
            type="button"
            onClick={() => navigate(ROUTE.login)}
            className="text-sm font-medium text-muted hover:text-body"
          >
            로그인 화면으로 돌아가기
          </button>
        </div>
      </VerificationStatusCard>
    )
  }

  if (!email) {
    return (
      <VerificationStatusCard
        icon={
          <span className="flex h-16 w-16 items-center justify-center rounded-full bg-down-tint">
            <ShieldAlert size={28} className="text-down" strokeWidth={2} />
          </span>
        }
        title="인증 정보를 찾을 수 없어요"
        description="인증 메일에 포함된 링크를 다시 확인해주세요."
      >
        <Button size="lg" className="min-w-[220px]" onClick={() => navigate(ROUTE.login)}>
          로그인 화면으로 돌아가기
        </Button>
      </VerificationStatusCard>
    )
  }

  return (
    <main className="flex min-h-[calc(100dvh-4rem)] items-center justify-center px-lg">
      <EmailSentCard
        title={sendError ? '인증 메일을 보내지 못했어요' : '이메일을 확인해주세요'}
        description={
          sendError ? (
            <>
              <span className="font-semibold text-ink">{email}</span>
              {' 주소로 메일을 보내지 못했어요.'}
              <br />
              아래 버튼을 눌러 다시 시도해주세요.
            </>
          ) : (
            <>
              <span className="font-semibold text-ink">{email}</span>
              {' 주소로'}
              <br />
              인증 링크를 보내드렸어요.
            </>
          )
        }
        resendLabel="인증 메일 재전송"
        resendToastMessage="인증 메일을 다시 보냈어요."
        initialError={sendError}
        startWithCooldown={!sendError}
        onResend={resendVerification}
        footer={
          <div className="flex flex-col items-center gap-xs">
            <Button
              variant="tertiary"
              onClick={bypassVerification}
              disabled={isBypassing}
            >
              {isBypassing ? '인증 우회 중…' : '이메일 인증 우회'}
            </Button>
            {error && <p role="alert" className="text-sm text-down">{error}</p>}
            <button
              type="button"
              onClick={() => navigate(ROUTE.login)}
              className="text-sm font-medium text-muted hover:text-body"
            >
              로그인 화면으로 돌아가기
            </button>
          </div>
        }
      />
    </main>
  )
}

interface VerificationStatusCardProps {
  icon: ReactNode
  title: string
  description: string
  children: ReactNode
}

function VerificationStatusCard({
  icon,
  title,
  description,
  children,
}: VerificationStatusCardProps) {
  return (
    <main className="flex min-h-[calc(100dvh-4rem)] items-center justify-center px-lg">
      <Card className="flex w-full max-w-[420px] flex-col items-center gap-lg py-xxl text-center shadow-soft">
        {icon}
        <div className="flex flex-col gap-xs">
          <h1 className="text-2xl font-bold text-ink">{title}</h1>
          <p className="text-base leading-relaxed text-body">{description}</p>
        </div>
        {children}
      </Card>
    </main>
  )
}

export default EmailVerificationPage
