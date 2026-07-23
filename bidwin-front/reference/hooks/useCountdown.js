import { useEffect, useState } from 'react'

export function useCountdown(deadline) {
  const [remaining, setRemaining] = useState(() =>
    Math.max(0, Math.round((deadline - Date.now()) / 1000)),
  )

  useEffect(() => {
    const tick = () => {
      setRemaining(Math.max(0, Math.round((deadline - Date.now()) / 1000)))
    }
    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [deadline])

  return {
    remaining,
    isUrgent: remaining <= 60 && remaining > 0,
    isEnded: remaining <= 0,
  }
}
