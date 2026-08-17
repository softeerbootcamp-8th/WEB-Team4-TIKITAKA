import { useContext, useEffect } from 'react'
import { ServerClockContext } from '../lib/clock/server-clock-context'

/*
 * 앱 전역의 서버 시계를 읽는다. 조회 응답에 serverTime이 실려오는 화면은 그 값을 넘겨
 * 첫 화면부터 보정된 시각을 쓰고, 이후 보정은 SSE 하트비트가 이어받는다(useAuctionEvents).
 */
export function useServerClock(initialServerTime?: number) {
  const context = useContext(ServerClockContext)
  if (context === null) {
    throw new Error('ServerClockProvider 안에서 useServerClock을 사용해야 합니다.')
  }
  const { serverOffsetMs, synchronize } = context

  useEffect(() => {
    if (initialServerTime === undefined) return
    synchronize(initialServerTime)
  }, [initialServerTime, synchronize])

  return { serverOffsetMs, synchronize }
}
