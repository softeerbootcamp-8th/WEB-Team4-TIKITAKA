import assert from 'node:assert/strict'
import test from 'node:test'
import {
  AUCTION_CATEGORY_CODES,
  AUCTION_CATEGORY_LABELS,
  AUCTION_CATEGORY_OPTIONS,
} from './auctionCategory.ts'

const EXPECTED_CATEGORIES = [
  ['HOUSEHOLD', '생활용품'],
  ['FOOD', '먹거리'],
  ['FURNITURE', '가구'],
  ['ELECTRONICS', '디지털/가전'],
  ['FASHION', '패션/잡화'],
  ['SPORTS', '스포츠/레저'],
  ['HOBBY', '취미/수집'],
  ['BOOK', '도서/문구'],
  ['OTHER', '기타'],
]

test('모든 경매 카테고리의 코드와 화면 라벨을 제공한다', () => {
  assert.deepEqual(AUCTION_CATEGORY_CODES, EXPECTED_CATEGORIES.map(([code]) => code))
  assert.deepEqual(
    AUCTION_CATEGORY_OPTIONS,
    EXPECTED_CATEGORIES.map(([value, label]) => ({ value, label })),
  )
  assert.deepEqual(
    AUCTION_CATEGORY_CODES.map((code) => AUCTION_CATEGORY_LABELS[code]),
    EXPECTED_CATEGORIES.map(([, label]) => label),
  )
})
