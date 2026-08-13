/*
 * 하락경매(DOWN) 실시간 가격 계산. 서버 응답의 동일한 가격 정책을 여러 화면에서
 * 일관되게 적용한다.
 */
export interface DownPricing {
  startPrice: number
  minimumPrice: number
  dropPrice: number
  priceDropIntervalMs: number
  startedAt: number
  /**
   * 서버가 응답을 만든 시각(epoch ms). 클라이언트 시계가 서버와 어긋나도 현재가를
   * 서버 기준으로 계산하기 위한 값이다. 값이 없으면 오프셋 없이 로컬 시계를 쓴다.
   */
  serverTime?: number
}

export function computeCurrentDownPrice(pricing: DownPricing, now: number): number {
  const elapsedDrops = Math.max(
    0,
    Math.floor((now - pricing.startedAt) / pricing.priceDropIntervalMs),
  )
  const price = pricing.startPrice - elapsedDrops * pricing.dropPrice
  return Math.max(price, pricing.minimumPrice)
}

export function nextDropAt(pricing: DownPricing, now: number): number {
  const elapsedDrops = Math.max(
    0,
    Math.floor((now - pricing.startedAt) / pricing.priceDropIntervalMs),
  )
  return pricing.startedAt + (elapsedDrops + 1) * pricing.priceDropIntervalMs
}

export interface PriceDropEntry {
  price: number
  droppedAt: number
}

/*
 * 실제로 지금까지 몇 번 떨어졌는지를 같은 공식(computeCurrentDownPrice와 동일한
 * elapsedDrops)으로 역산해서 만든다 — 별도로 기록을 저장해두지 않아도 항상
 * 현재가와 내역이 어긋나지 않는다.
 */
export function computeDropHistory(pricing: DownPricing, now: number): PriceDropEntry[] {
  const elapsedDrops = Math.max(
    0,
    Math.floor((now - pricing.startedAt) / pricing.priceDropIntervalMs),
  )
  const history: PriceDropEntry[] = []

  for (let i = 1; i <= elapsedDrops; i++) {
    const price = Math.max(pricing.startPrice - i * pricing.dropPrice, pricing.minimumPrice)
    history.push({ price, droppedAt: pricing.startedAt + i * pricing.priceDropIntervalMs })
  }

  return history
}
