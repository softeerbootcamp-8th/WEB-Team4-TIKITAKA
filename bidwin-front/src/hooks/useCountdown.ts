import { useEffect, useState } from 'react'

const URGENT_THRESHOLD_SECONDS = 60
const TICK_INTERVAL_MS = 1000

function secondsUntil(deadline: number) {
  return Math.max(0, Math.round((deadline - Date.now()) / 1000))
}

export function useCountdown(deadline: number) {
  const [remaining, setRemaining] = useState(() => secondsUntil(deadline))

  useEffect(() => {
    const tick = () => setRemaining(secondsUntil(deadline))
    tick()
    const id = setInterval(tick, TICK_INTERVAL_MS)
    return () => clearInterval(id)
  }, [deadline])

  return {
    remaining,
    isUrgent: remaining <= URGENT_THRESHOLD_SECONDS && remaining > 0,
    isEnded: remaining <= 0,
  }
}
