import { useCallback, useEffect, useRef, useState } from 'react'
import { requestAuctionClock } from '../lib/api/auctions'
import { estimateServerOffsetMs } from '../lib/serverClock'

const CLOCK_SYNC_INTERVAL_MS = 15_000

export function useServerClock(initialServerTime?: number) {
  const [serverOffsetMs, setServerOffsetMs] = useState(
    () => initialServerTime === undefined ? 0 : initialServerTime - Date.now(),
  )
  const inFlightRef = useRef(false)
  const activeRef = useRef(true)

  const synchronize = useCallback(async () => {
    if (inFlightRef.current) return

    inFlightRef.current = true
    const clientRequestedAt = Date.now()
    const startedAt = performance.now()
    const result = await requestAuctionClock()
    const roundTripTimeMs = performance.now() - startedAt
    inFlightRef.current = false

    if (!activeRef.current || !result.ok) return
    setServerOffsetMs(
      estimateServerOffsetMs(result.data, clientRequestedAt, roundTripTimeMs),
    )
  }, [])

  useEffect(() => {
    activeRef.current = true
    void synchronize()
    const intervalId = window.setInterval(() => void synchronize(), CLOCK_SYNC_INTERVAL_MS)
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') void synchronize()
    }
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      activeRef.current = false
      window.clearInterval(intervalId)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [synchronize])

  useEffect(() => {
    if (initialServerTime === undefined) return
    setServerOffsetMs(initialServerTime - Date.now())
    void synchronize()
  }, [initialServerTime, synchronize])

  return serverOffsetMs
}
