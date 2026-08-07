import { useEffect, useRef, useState } from 'react'
import type { DownPricing } from '../lib/auctionPricing'
import { computeCurrentDownPrice, nextDropAt } from '../lib/auctionPricing'

/*
 * 하락경매는 "다음 하락까지"가 한 번 끝나면 또 새로 시작하는 반복 카운트다운이라
 * useCountdown(고정 마감시각 하나만 세는 용도)을 못 쓴다. 매 tick마다 가격·다음
 * 하락 시각을 통째로 다시 계산해야 0에서 멈추지 않고 계속 순환한다.
 */
const TICK_INTERVAL_MS = 1000
const URGENT_THRESHOLD_SECONDS = 5

/*
 * 서버가 응답을 만든 시각(serverTime)과 그 응답을 받은 순간의 로컬 시계 차이를 오프셋으로
 * 잡아, 이후 tick의 "지금"을 서버 기준으로 보정한다. serverTime이 없으면(목업 등) 0이라
 * 로컬 시계를 그대로 쓴다. 오프셋은 데이터가 바뀔 때 한 번만 고정해야 시계가 얼지 않는다.
 */
function serverOffsetMs(pricing: DownPricing): number {
  return pricing.serverTime == null ? 0 : pricing.serverTime - Date.now()
}

function computeState(pricing: DownPricing, offsetMs: number) {
  const now = Date.now() + offsetMs
  const currentPrice = computeCurrentDownPrice(pricing, now)
  const msUntilNextDrop = Math.max(0, nextDropAt(pricing, now) - now)
  const remaining = Math.ceil(msUntilNextDrop / 1000)

  return {
    currentPrice,
    remaining,
    atFloor: currentPrice <= pricing.minimumPrice,
    isUrgent: remaining <= URGENT_THRESHOLD_SECONDS,
  }
}

export function useDownAuctionClock(pricing: DownPricing) {
  const offsetRef = useRef(serverOffsetMs(pricing))
  const [state, setState] = useState(() => computeState(pricing, offsetRef.current))

  useEffect(() => {
    offsetRef.current = serverOffsetMs(pricing)
    const tick = () => setState(computeState(pricing, offsetRef.current))
    tick()
    const id = setInterval(tick, TICK_INTERVAL_MS)
    return () => clearInterval(id)
  }, [pricing])

  return state
}
