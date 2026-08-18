import assert from 'node:assert/strict'
import test from 'node:test'
import {
  BUYING_STATUS_LABEL,
  SELLING_STATUS_LABEL,
} from './constants.ts'
import { ongoingFirst, toBuyingCard, toSellingCard } from './view.ts'

const DOWN_PRICING = {
  startPrice: 100_000,
  minimumPrice: 50_000,
  dropPrice: 10_000,
  priceDropIntervalMs: 60_000,
  startedAt: 1_000_000,
  serverTime: 1_000_000,
}

function sellingItem(overrides = {}) {
  return {
    auctionId: 1,
    title: '거의 새것인 무선 이어폰',
    thumbnailUrl: null,
    auctionType: 'DOWN',
    startPrice: 100_000,
    price: 90_000,
    status: 'ON_SALE',
    downPricing: DOWN_PRICING,
    ...overrides,
  }
}

function buyingItem(overrides = {}) {
  return {
    auctionId: 2,
    title: '접이식 캠핑 의자',
    thumbnailUrl: null,
    auctionType: 'UP',
    startPrice: 10_000,
    price: 22_000,
    status: 'PAYMENT_PENDING',
    ...overrides,
  }
}

function cardOf(overrides) {
  return { isOngoing: false, ...overrides }
}

/* 판매 중인 물품은 값이 계속 움직이므로 "현재가"로 부르고, 아직 손이 가야 하는 건으로 본다. */
test('판매 중인 물품은 값이 확정되지 않은 진행 중 카드가 된다', () => {
  // given
  const item = sellingItem({ status: 'ON_SALE' })

  // when
  const card = toSellingCard(item)

  // then
  assert.equal(card.isSettled, false)
  assert.equal(card.isOngoing, true)
})

test('낙찰이 끝난 물품은 값이 확정된 끝난 카드가 된다', () => {
  // given
  const item = sellingItem({ status: 'SOLD' })

  // when
  const card = toSellingCard(item)

  // then
  assert.equal(card.isSettled, true)
  assert.equal(card.isOngoing, false)
})

test('유찰된 물품도 값이 확정된 끝난 카드가 된다', () => {
  // given
  const item = sellingItem({ status: 'FAILED' })

  // when
  const card = toSellingCard(item)

  // then
  assert.equal(card.isSettled, true)
  assert.equal(card.isOngoing, false)
})

/* 판매가 끝난 물품에 실시간 하락 시계가 붙으면 이미 끝난 가격이 계속 내려가 보인다. */
test('판매가 끝난 물품에는 실시간 가격 인하 정보를 넘기지 않는다', () => {
  // given
  const onSale = sellingItem({ status: 'ON_SALE' })
  const sold = sellingItem({ status: 'SOLD' })

  // when
  const cards = [onSale, sold].map(toSellingCard)

  // then
  assert.deepEqual(cards[0].downPricing, DOWN_PRICING)
  assert.equal(cards[1].downPricing, undefined)
})

test('가격 인하 정보가 없는 판매 중 물품도 카드로 만들 수 있다', () => {
  // given
  const item = sellingItem({ status: 'ON_SALE', downPricing: null })

  // when
  const card = toSellingCard(item)

  // then
  assert.equal(card.downPricing, undefined)
})

test('판매 물품 상태마다 정해진 라벨을 붙인다', () => {
  // given
  const statuses = ['ON_SALE', 'SOLD', 'FAILED']

  // when
  const labels = statuses.map((status) => toSellingCard(sellingItem({ status })).statusLabel)

  // then
  assert.deepEqual(labels, statuses.map((status) => SELLING_STATUS_LABEL[status]))
})

/* 구매 물품은 낙찰된 순간 값이 정해지므로, 거래가 남아 있어도 "거래가"로 부른다. */
test('구매 물품은 거래가 남아 있어도 값이 확정된 것으로 본다', () => {
  // given
  const statuses = ['PAYMENT_PENDING', 'IN_PROGRESS', 'DONE']

  // when
  const settled = statuses.map((status) => toBuyingCard(buyingItem({ status })).isSettled)

  // then
  assert.deepEqual(settled, [true, true, true])
})

test('거래가 끝나기 전인 구매 물품은 진행 중으로 본다', () => {
  // given
  const statuses = ['PAYMENT_PENDING', 'IN_PROGRESS', 'DONE']

  // when
  const ongoing = statuses.map((status) => toBuyingCard(buyingItem({ status })).isOngoing)

  // then
  assert.deepEqual(ongoing, [true, true, false])
})

test('구매 물품 상태마다 정해진 라벨을 붙인다', () => {
  // given
  const statuses = ['PAYMENT_PENDING', 'IN_PROGRESS', 'DONE']

  // when
  const labels = statuses.map((status) => toBuyingCard(buyingItem({ status })).statusLabel)

  // then
  assert.deepEqual(labels, statuses.map((status) => BUYING_STATUS_LABEL[status]))
})

/* 미리보기는 몇 장만 보여주므로, 아직 할 일이 남은 물품이 잘려나가면 안 된다. */
test('아직 손이 가야 하는 물품을 앞으로 당긴다', () => {
  // given
  const items = [
    cardOf({ auctionId: 1 }),
    cardOf({ auctionId: 2, isOngoing: true }),
    cardOf({ auctionId: 3 }),
  ]

  // when
  const sorted = ongoingFirst(items)

  // then
  assert.deepEqual(sorted.map((item) => item.auctionId), [2, 1, 3])
})

/* 같은 무리 안에서 순서가 흔들리면 새로고침할 때마다 카드 자리가 바뀐다. */
test('진행 여부가 같은 물품끼리는 서버가 준 순서를 유지한다', () => {
  // given
  const items = [
    cardOf({ auctionId: 1, isOngoing: true }),
    cardOf({ auctionId: 2 }),
    cardOf({ auctionId: 3, isOngoing: true }),
    cardOf({ auctionId: 4 }),
  ]

  // when
  const sorted = ongoingFirst(items)

  // then
  assert.deepEqual(sorted.map((item) => item.auctionId), [1, 3, 2, 4])
})

test('정렬해도 서버가 준 원래 목록은 그대로 남는다', () => {
  // given
  const items = [cardOf({ auctionId: 1 }), cardOf({ auctionId: 2, isOngoing: true })]

  // when
  ongoingFirst(items)

  // then
  assert.deepEqual(items.map((item) => item.auctionId), [1, 2])
})
