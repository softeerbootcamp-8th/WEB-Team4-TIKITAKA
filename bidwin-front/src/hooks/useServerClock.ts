import { useCallback, useEffect, useState } from 'react'

export function useServerClock(initialServerTime?: number) {
  const [serverOffsetMs, setServerOffsetMs] = useState(
    () => initialServerTime === undefined ? 0 : initialServerTime - Date.now(),
  )
  const synchronize = useCallback((serverTime: number) => {
    setServerOffsetMs(serverTime - Date.now())
  }, [])

  useEffect(() => {
    if (initialServerTime === undefined) return
    synchronize(initialServerTime)
  }, [initialServerTime, synchronize])

  return { serverOffsetMs, synchronize }
}
