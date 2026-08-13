import assert from 'node:assert/strict'
import test from 'node:test'
import { computeCurrentDownPrice, nextDropAt } from './auctionPricing.ts'

test('서버 보정 시각이 시작 전이면 하향 경매는 시작가와 첫 하락 시각을 유지한다', () => {
  // given
  const pricing = {
    startPrice: 100_000,
    minimumPrice: 50_000,
    dropPrice: 10_000,
    priceDropIntervalMs: 60_000,
    startedAt: 1_000_000,
  }
  const correctedNow = pricing.startedAt - 1_000

  // when
  const currentPrice = computeCurrentDownPrice(pricing, correctedNow)
  const firstDropAt = nextDropAt(pricing, correctedNow)

  // then
  assert.equal(currentPrice, pricing.startPrice)
  assert.equal(firstDropAt, pricing.startedAt + pricing.priceDropIntervalMs)
})
