import assert from 'node:assert/strict'
import test from 'node:test'
import { computeCurrentDownPrice, computeDropHistory, nextDropAt } from './auctionPricing.ts'

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

test('최저가에 닿은 뒤에는 주기가 지나도 가격 변동 내역이 늘지 않는다', () => {
  // given
  const pricing = {
    startPrice: 100_000,
    minimumPrice: 80_000,
    dropPrice: 10_000,
    priceDropIntervalMs: 60_000,
    startedAt: 1_000_000,
  }
  /* 최저가까지 2번이면 닿는데, 주기는 10번 지난 시점 */
  const now = pricing.startedAt + 10 * pricing.priceDropIntervalMs

  // when
  const history = computeDropHistory(pricing, now)

  // then
  assert.deepEqual(history, [
    { price: 90_000, droppedAt: pricing.startedAt + pricing.priceDropIntervalMs },
    { price: 80_000, droppedAt: pricing.startedAt + 2 * pricing.priceDropIntervalMs },
  ])
  assert.equal(computeCurrentDownPrice(pricing, now), pricing.minimumPrice)
})

test('시작가가 최저가와 같으면 가격 변동 내역이 비어 있다', () => {
  // given
  const pricing = {
    startPrice: 50_000,
    minimumPrice: 50_000,
    dropPrice: 10_000,
    priceDropIntervalMs: 60_000,
    startedAt: 1_000_000,
  }

  // when
  const history = computeDropHistory(
    pricing,
    pricing.startedAt + 5 * pricing.priceDropIntervalMs,
  )

  // then
  assert.deepEqual(history, [])
})
