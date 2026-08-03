/*
 * 하락경매(DOWN) 실시간 가격 계산 — 메인 페이지와 경매 상세 페이지가 같은 경매를
 * 같은 가격으로 보여줘야 해서 공용으로 뺐다. 각 페이지의 목업 데이터(시작가/최저가/
 * 하락폭/하락주기)는 auctionId 기준으로 서로 값을 맞춰서 써야 한다.
 */
export interface DownPricing {
  startPrice: number
  minimumPrice: number
  dropPrice: number
  priceDropIntervalMs: number
  startedAt: number
}

export function computeCurrentDownPrice(pricing: DownPricing, now: number): number {
  const elapsedDrops = Math.floor((now - pricing.startedAt) / pricing.priceDropIntervalMs)
  const price = pricing.startPrice - elapsedDrops * pricing.dropPrice
  return Math.max(price, pricing.minimumPrice)
}

export function nextDropAt(pricing: DownPricing, now: number): number {
  const elapsedDrops = Math.floor((now - pricing.startedAt) / pricing.priceDropIntervalMs)
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
  const elapsedDrops = Math.floor((now - pricing.startedAt) / pricing.priceDropIntervalMs)
  const history: PriceDropEntry[] = []

  for (let i = 1; i <= elapsedDrops; i++) {
    const price = Math.max(pricing.startPrice - i * pricing.dropPrice, pricing.minimumPrice)
    history.push({ price, droppedAt: pricing.startedAt + i * pricing.priceDropIntervalMs })
  }

  return history
}
