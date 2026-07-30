import type { ReactNode } from 'react'

type BadgeTone = 'live' | 'ended' | 'muted' | 'primary'

interface BadgeProps {
  tone?: BadgeTone
  children: ReactNode
}

const TONE_CLASSES: Record<BadgeTone, string> = {
  live: 'bg-ink text-on-dark',
  ended: 'bg-surface-strong text-muted',
  muted: 'bg-surface-strong text-body',
  primary: 'bg-primary-tint text-primary',
}

function Badge({ tone = 'muted', children }: BadgeProps) {
  return (
    <span
      className={`inline-flex h-7 items-center gap-1.5 rounded-pill px-sm text-xs font-semibold ${TONE_CLASSES[tone]}`}
    >
      {tone === 'live' && (
        <span className="h-1.5 w-1.5 rounded-full bg-up motion-safe:animate-pulse" />
      )}
      {children}
    </span>
  )
}

export default Badge
export type { BadgeProps, BadgeTone }
