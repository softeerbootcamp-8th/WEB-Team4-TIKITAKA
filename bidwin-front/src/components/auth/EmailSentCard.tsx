import { useState } from 'react'
import type { ReactNode } from 'react'
import { Mail } from 'lucide-react'
import Button from '../ui/Button'
import Card from '../ui/Card'
import { useToast } from '../../hooks/useToast'
import { useCountdown } from '../../hooks/useCountdown'

const RESEND_COOLDOWN_SECONDS = 60

function makeCooldownDeadline() {
  return Date.now() + RESEND_COOLDOWN_SECONDS * 1000
}

interface EmailSentCardProps {
  title: string
  description: ReactNode
  onResend: () => void
  resendLabel?: string
  resendToastMessage?: string
  footer?: ReactNode
}

function EmailSentCard({
  title,
  description,
  onResend,
  resendLabel = '메일 재전송',
  resendToastMessage = '메일을 다시 보냈어요.',
  footer,
}: EmailSentCardProps) {
  const { showToast } = useToast()
  const [cooldownDeadline, setCooldownDeadline] = useState(makeCooldownDeadline)
  const { remaining, isEnded } = useCountdown(cooldownDeadline)

  const handleResend = () => {
    if (!isEnded) return
    setCooldownDeadline(makeCooldownDeadline())
    onResend()
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
          disabled={!isEnded}
          className="min-w-[220px]"
        >
          {isEnded ? resendLabel : `재전송까지 ${remaining}초`}
        </Button>
        {footer}
      </div>
    </Card>
  )
}

export default EmailSentCard
export type { EmailSentCardProps }
