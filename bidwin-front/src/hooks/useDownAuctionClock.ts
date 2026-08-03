import { useEffect, useState } from 'react'
import type { DownPricing } from '../lib/auctionPricing'
import { computeCurrentDownPrice, nextDropAt } from '../lib/auctionPricing'

/*
 * 하락경매는 "다음 하락까지"가 한 번 끝나면 또 새로 시작하는 반복 카운트다운이라
 * useCountdown(고정 마감시각 하나만 세는 용도)을 못 쓴다. 매 tick마다 가격·다음
 * 하락 시각을 통째로 다시 계산해야 0에서 멈추지 않고 계속 순환한다.
 */
const TICK_INTERVAL_MS = 1000
const URGENT_THRESHOLD_SECONDS = 5

function computeState(pricing: DownPricing) {
  const now = Date.now()
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
  const [state, setState] = useState(() => computeState(pricing))

  useEffect(() => {
    const tick = () => setState(computeState(pricing))
    tick()
    const id = setInterval(tick, TICK_INTERVAL_MS)
    return () => clearInterval(id)
  }, [pricing])

  return state
}
