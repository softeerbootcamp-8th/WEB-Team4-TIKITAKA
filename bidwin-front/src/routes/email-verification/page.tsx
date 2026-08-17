import { useState } from 'react'
import type { ReactNode } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { CheckCircle2, MailCheck, ShieldAlert } from 'lucide-react'
import EmailSentCard from '../../components/auth/EmailSentCard'
import type { ResendResult } from '../../components/auth/EmailSentCard'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import {
  requestEmailVerification,
  requestEmailVerificationConfirm,
} from '../../lib/api/auth'
import {
  DEFAULT_RESEND_COOLDOWN_SECONDS,
  EMAIL_VERIFICATION_ROUTE,
} from '../../lib/auth/emailVerification'
import type { EmailVerificationLocationState } from '../../lib/auth/emailVerification'

const TEXT = {
  completeTitle: '이메일 인증이 완료됐어요',
  completeDescription: '이제 가입한 계정으로 로그인할 수 있어요.',
  goToLogin: '로그인하러 가기',
  confirmTitle: '이메일 인증을 완료해주세요',
  confirmDescription: '아래 버튼을 누르면 이메일 인증이 완료됩니다.',
  confirmSubmit: '이메일 인증 완료하기',
  confirmSubmitting: '인증 중…',
  backToLogin: '로그인 화면으로 돌아가기',
  missingStateTitle: '인증 정보를 찾을 수 없어요',
  missingStateDescription: '인증 메일에 포함된 링크를 다시 확인해주세요.',
  sentTitle: '이메일을 확인해주세요',
  throttledTitle: '이미 인증 메일을 보냈어요',
  failedTitle: '인증 메일을 보내지 못했어요',
  resendLabel: '인증 메일 재전송',
  resendToastMessage: '인증 메일을 다시 보냈어요.',
  throttledMessage: '조금 전에 보낸 인증 메일이 있어요. 대기 시간이 끝나면 다시 보낼 수 있어요.',
  missingEmail: '이메일 정보가 없어 인증 메일을 다시 보낼 수 없어요.',
}

const ROUTE = {
  login: '/login',
  emailVerification: EMAIL_VERIFICATION_ROUTE,
}

/* 인증 메일 발송 요청의 결과. 화면 문구와 재전송 대기 시간을 여기서 갈라준다. */
type SendState = 'sent' | 'throttled' | 'failed'

const SEND_STATE_TITLE: Record<SendState, string> = {
  sent: TEXT.sentTitle,
  throttled: TEXT.throttledTitle,
  failed: TEXT.failedTitle,
}

function initialSendState(state: EmailVerificationLocationState | null): SendState {
  if (state?.sendError) return 'failed'
  return state?.sent === false ? 'throttled' : 'sent'
}

function initialCooldownSeconds(state: EmailVerificationLocationState | null) {
  /* 발송에 실패했으면 기다리지 않고 바로 다시 시도할 수 있어야 한다. */
  if (state?.sendError) return 0
  return state?.retryAfterSeconds ?? DEFAULT_RESEND_COOLDOWN_SECONDS
}

function EmailVerificationPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const state = location.state as EmailVerificationLocationState | null
  const email = state?.email
  const token = searchParams.get('token')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isComplete, setIsComplete] = useState(state?.verified === true)
  const [sendState, setSendState] = useState(() => initialSendState(state))
  const [error, setError] = useState<string | null>(null)

  async function resendVerification(): Promise<ResendResult> {
    if (!email) return { sent: false, error: TEXT.missingEmail }

    const result = await requestEmailVerification(email)
    if (!result.ok) {
      setSendState('failed')
      return { sent: false, error: result.message }
    }

    setSendState(result.data.sent ? 'sent' : 'throttled')
    return { sent: result.data.sent, retryAfterSeconds: result.data.retryAfterSeconds }
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

  if (isComplete) {
    return (
      <VerificationStatusCard
        icon={
          <span className="flex h-16 w-16 items-center justify-center rounded-full bg-up-tint">
            <CheckCircle2 size={28} className="text-up" strokeWidth={2} />
          </span>
        }
        title={TEXT.completeTitle}
        description={TEXT.completeDescription}
      >
        <Button size="lg" className="min-w-[220px]" onClick={() => navigate(ROUTE.login)}>
          {TEXT.goToLogin}
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
        title={TEXT.confirmTitle}
        description={TEXT.confirmDescription}
      >
        <div className="flex flex-col items-center gap-sm">
          <Button
            size="lg"
            className="min-w-[220px]"
            onClick={confirmVerification}
            disabled={isSubmitting}
          >
            {isSubmitting ? TEXT.confirmSubmitting : TEXT.confirmSubmit}
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
            {TEXT.backToLogin}
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
        title={TEXT.missingStateTitle}
        description={TEXT.missingStateDescription}
      >
        <Button size="lg" className="min-w-[220px]" onClick={() => navigate(ROUTE.login)}>
          {TEXT.backToLogin}
        </Button>
      </VerificationStatusCard>
    )
  }

  return (
    <main className="flex min-h-[calc(100dvh-4rem)] items-center justify-center px-lg">
      <EmailSentCard
        title={SEND_STATE_TITLE[sendState]}
        description={<SendStateDescription sendState={sendState} email={email} />}
        resendLabel={TEXT.resendLabel}
        resendToastMessage={TEXT.resendToastMessage}
        throttledMessage={TEXT.throttledMessage}
        initialError={state?.sendError}
        initialCooldownSeconds={initialCooldownSeconds(state)}
        onResend={resendVerification}
        footer={
          <button
            type="button"
            onClick={() => navigate(ROUTE.login)}
            className="text-sm font-medium text-muted hover:text-body"
          >
            {TEXT.backToLogin}
          </button>
        }
      />
    </main>
  )
}

interface SendStateDescriptionProps {
  sendState: SendState
  email: string
}

function SendStateDescription({ sendState, email }: SendStateDescriptionProps) {
  const emailText = <span className="font-semibold text-ink">{email}</span>

  if (sendState === 'failed') {
    return (
      <>
        {emailText}
        {' 주소로 메일을 보내지 못했어요.'}
        <br />
        아래 버튼을 눌러 다시 시도해주세요.
      </>
    )
  }

  if (sendState === 'throttled') {
    return (
      <>
        {emailText}
        {' 주소로 보낸'}
        <br />
        인증 링크를 먼저 확인해주세요.
      </>
    )
  }

  return (
    <>
      {emailText}
      {' 주소로'}
      <br />
      인증 링크를 보내드렸어요.
    </>
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
