import { useState } from 'react'
import type { ReactNode } from 'react'
import { Mail } from 'lucide-react'
import Button from '../ui/Button'
import Card from '../ui/Card'
import { useToast } from '../../hooks/useToast'
import { useCountdown } from '../../hooks/useCountdown'
import { DEFAULT_RESEND_COOLDOWN_SECONDS } from '../../lib/auth/emailVerification'

const TEXT = {
  resending: '전송 중…',
  resendLabel: '메일 재전송',
  resendToastMessage: '메일을 다시 보냈어요.',
  throttled: '조금 전에 보낸 메일이 있어요. 대기 시간이 끝나면 다시 보낼 수 있어요.',
  resendCountdown: (seconds: number) => `재전송까지 ${seconds}초`,
}

const MILLIS_PER_SECOND = 1000

function cooldownDeadlineAfter(seconds: number) {
  return Date.now() + seconds * MILLIS_PER_SECOND
}

/* 재전송 요청의 결과. sent가 false면 재전송 제한에 걸려 메일이 나가지 않은 것이다. */
interface ResendResult {
  sent: boolean
  error?: string
  retryAfterSeconds?: number
}

interface Notice {
  message: string
  isError: boolean
}

interface EmailSentCardProps {
  title: string
  description: ReactNode
  onResend: () => Promise<ResendResult>
  resendLabel?: string
  resendToastMessage?: string
  throttledMessage?: string
  initialError?: string
  initialCooldownSeconds?: number
  footer?: ReactNode
}

function EmailSentCard({
  title,
  description,
  onResend,
  resendLabel = TEXT.resendLabel,
  resendToastMessage = TEXT.resendToastMessage,
  throttledMessage = TEXT.throttled,
  initialError,
  initialCooldownSeconds = DEFAULT_RESEND_COOLDOWN_SECONDS,
  footer,
}: EmailSentCardProps) {
  const { showToast } = useToast()
  const [cooldownDeadline, setCooldownDeadline] = useState(() =>
    cooldownDeadlineAfter(initialCooldownSeconds),
  )
  const [isResending, setIsResending] = useState(false)
  const [notice, setNotice] = useState<Notice | null>(
    initialError ? { message: initialError, isError: true } : null,
  )
  const { remaining, isEnded } = useCountdown(cooldownDeadline)

  const handleResend = async () => {
    if (!isEnded || isResending) return
    setIsResending(true)
    setNotice(null)
    const result = await onResend()
    setIsResending(false)

    if (result.error) {
      /* 요청 자체가 실패했으면 대기 없이 다시 시도할 수 있게 둔다. */
      setNotice({ message: result.error, isError: true })
      return
    }

    setCooldownDeadline(
      cooldownDeadlineAfter(result.retryAfterSeconds ?? DEFAULT_RESEND_COOLDOWN_SECONDS),
    )
    if (!result.sent) {
      /* 재전송 제한에 걸린 경우라 메일은 나가지 않았다. */
      setNotice({ message: throttledMessage, isError: false })
      return
    }

    showToast(resendToastMessage)
  }

  return (
    <Card className="flex w-full max-w-[420px] flex-col items-center gap-lg py-xxl text-center shadow-soft">
      <span className="flex h-16 w-16 items-center justify-center rounded-full bg-primary-tint">
        <Mail size={28} className="text-primary" strokeWidth={2} />
      </span>

      <div className="flex flex-col gap-xs">
        <h1 className="text-2xl font-bold text-ink">{title}</h1>
        <p className="text-base leading-relaxed text-body">{description}</p>
      </div>

      <div className="flex flex-col items-center gap-sm">
        <Button
          variant="secondary"
          onClick={handleResend}
          disabled={!isEnded || isResending}
          className="min-w-[220px]"
        >
          {isResending
            ? TEXT.resending
            : isEnded
              ? resendLabel
              : TEXT.resendCountdown(remaining)}
        </Button>
        {notice && (
          <p
            role={notice.isError ? 'alert' : 'status'}
            className={`text-sm ${notice.isError ? 'text-down' : 'text-muted'}`}
          >
            {notice.message}
          </p>
        )}
        {footer}
      </div>
    </Card>
  )
}

export default EmailSentCard
export type { EmailSentCardProps, ResendResult }
