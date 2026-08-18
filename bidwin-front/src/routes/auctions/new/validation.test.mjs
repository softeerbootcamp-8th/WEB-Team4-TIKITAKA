import assert from 'node:assert/strict'
import test from 'node:test'
import {
  CONTACT_MAX_LENGTH,
  ERROR_MESSAGE,
  MAX_PRICE,
  MIN_PRICE,
  TITLE_MAX_LENGTH,
} from './constants.ts'
import { validateAuctionFields } from './validation.ts'

/* 통과는 null, 실패는 화면에 그대로 보여줄 메시지를 돌려준다. */

/* 경쟁 입찰(UP): 즉시 구매가만 선택 입력이고 하향 관련 값은 쓰지 않는다. */
function upAuction(overrides = {}) {
  return {
    title: '거의 새것인 무선 이어폰',
    description: '한 달 쓰고 상자까지 그대로 보관했어요.',
    category: 'ELECTRONICS',
    contact: '01012345678',
    auctionType: 'UP',
    tradeType: 'DELIVERY',
    startPrice: '10000',
    buyNowPrice: '',
    minimumPrice: '',
    dropPrice: '',
    priceDropInterval: '',
    ...overrides,
  }
}

/* 가격 인하(DOWN): 최저가·인하 금액·인하 주기가 모두 필요하고 즉시 구매가는 쓰지 않는다. */
function downAuction(overrides = {}) {
  return {
    ...upAuction(),
    auctionType: 'DOWN',
    startPrice: '100000',
    minimumPrice: '50000',
    dropPrice: '10000',
    priceDropInterval: '5',
    ...overrides,
  }
}

test('필수 항목과 시작가를 채운 경쟁 입찰 경매는 통과한다', () => {
  // given
  const fields = upAuction()

  // when
  const error = validateAuctionFields(fields)

  // then
  assert.equal(error, null)
})

test('제목·설명·카테고리·연락처 중 하나라도 비면 막는다', () => {
  // given
  const emptyByField = ['title', 'description', 'category', 'contact'].map((field) =>
    upAuction({ [field]: '' }),
  )

  // when
  const errors = emptyByField.map(validateAuctionFields)

  // then
  assert.deepEqual(errors, errors.map(() => ERROR_MESSAGE.emptyRequiredField))
})

test('제목이 최대 길이를 넘으면 막는다', () => {
  // given
  const fields = upAuction({ title: '가'.repeat(TITLE_MAX_LENGTH + 1) })

  // when
  const error = validateAuctionFields(fields)

  // then
  assert.equal(error, ERROR_MESSAGE.titleTooLong)
})

/* 오픈채팅방으로 연락받는 판매자가 있어 휴대폰 번호와 http(s) 링크를 모두 받는다. */
test('연락처는 하이픈 없는 휴대폰 번호와 http(s) 링크를 모두 받는다', () => {
  // given
  const phone = upAuction({ contact: '01012345678' })
  const openChatLink = upAuction({ contact: 'https://open.kakao.com/o/abcdefg' })

  // when
  const errors = [phone, openChatLink].map(validateAuctionFields)

  // then
  assert.deepEqual(errors, [null, null])
})

test('번호도 링크도 아닌 연락처는 막는다', () => {
  // given
  const fields = upAuction({ contact: '카카오톡 아이디로 연락주세요' })

  // when
  const error = validateAuctionFields(fields)

  // then
  assert.equal(error, ERROR_MESSAGE.invalidContact)
})

test('하이픈이 들어간 휴대폰 번호는 연락처로 받지 않는다', () => {
  // given
  const fields = upAuction({ contact: '010-1234-5678' })

  // when
  const error = validateAuctionFields(fields)

  // then
  assert.equal(error, ERROR_MESSAGE.invalidContact)
})

test('연락처가 최대 길이를 넘으면 형식보다 길이를 먼저 알린다', () => {
  // given
  const tooLongLink = `https://open.kakao.com/o/${'a'.repeat(CONTACT_MAX_LENGTH)}`
  const fields = upAuction({ contact: tooLongLink })

  // when
  const error = validateAuctionFields(fields)

  // then
  assert.equal(error, ERROR_MESSAGE.contactTooLong)
})

test('시작가가 최소 금액보다 낮으면 막는다', () => {
  // given
  const fields = upAuction({ startPrice: String(MIN_PRICE - 1) })

  // when
  const error = validateAuctionFields(fields)

  // then
  assert.equal(error, ERROR_MESSAGE.invalidStartPrice)
})

test('시작가가 비어 있거나 숫자가 아니면 막는다', () => {
  // given
  const empty = upAuction({ startPrice: '' })
  const notANumber = upAuction({ startPrice: '만원' })

  // when
  const errors = [empty, notANumber].map(validateAuctionFields)

  // then
  assert.deepEqual(errors, [ERROR_MESSAGE.invalidStartPrice, ERROR_MESSAGE.invalidStartPrice])
})

/* 아무도 살 수 없는 가격(상한과 같거나 그 위)으로 등록되는 것을 막는다. */
test('시작가가 상한을 넘으면 막고 상한 자체는 통과시킨다', () => {
  // given
  const atLimit = upAuction({ startPrice: String(MAX_PRICE) })
  const overLimit = upAuction({ startPrice: String(MAX_PRICE + MIN_PRICE) })

  // when
  const errors = [atLimit, overLimit].map(validateAuctionFields)

  // then
  assert.deepEqual(errors, [null, ERROR_MESSAGE.startPriceTooHigh])
})

test('시작가가 최소 금액 단위로 떨어지지 않으면 막는다', () => {
  // given
  const fields = upAuction({ startPrice: String(MIN_PRICE + 1) })

  // when
  const error = validateAuctionFields(fields)

  // then
  assert.equal(error, ERROR_MESSAGE.invalidStartPriceUnit)
})

test('경쟁 입찰의 즉시 구매가는 비워둬도 통과한다', () => {
  // given
  const fields = upAuction({ buyNowPrice: '' })

  // when
  const error = validateAuctionFields(fields)

  // then
  assert.equal(error, null)
})

test('즉시 구매가를 적었다면 시작가보다 높아야 한다', () => {
  // given
  const sameAsStartPrice = upAuction({ startPrice: '10000', buyNowPrice: '10000' })
  const higherThanStartPrice = upAuction({ startPrice: '10000', buyNowPrice: '10001' })

  // when
  const errors = [sameAsStartPrice, higherThanStartPrice].map(validateAuctionFields)

  // then
  assert.deepEqual(errors, [ERROR_MESSAGE.buyNowPriceMustExceedStartPrice, null])
})

test('즉시 구매가가 최소 금액보다 낮으면 막는다', () => {
  // given
  const fields = upAuction({ buyNowPrice: String(MIN_PRICE - 1) })

  // when
  const error = validateAuctionFields(fields)

  // then
  assert.equal(error, ERROR_MESSAGE.invalidBuyNowPrice)
})

test('즉시 구매가가 상한을 넘으면 막는다', () => {
  // given
  const fields = upAuction({ buyNowPrice: String(MAX_PRICE + MIN_PRICE) })

  // when
  const error = validateAuctionFields(fields)

  // then
  assert.equal(error, ERROR_MESSAGE.buyNowPriceTooHigh)
})

/* 경쟁 입찰에는 가격이 내려가는 개념이 없으므로 하향 전용 값은 비어 있어도 본 적이 없어야 한다. */
test('경쟁 입찰은 최저가·인하 금액·인하 주기를 검사하지 않는다', () => {
  // given
  const fields = upAuction({ minimumPrice: '', dropPrice: '', priceDropInterval: '' })

  // when
  const error = validateAuctionFields(fields)

  // then
  assert.equal(error, null)
})

test('최저가·인하 금액·인하 주기를 채운 가격 인하 경매는 통과한다', () => {
  // given
  const fields = downAuction()

  // when
  const error = validateAuctionFields(fields)

  // then
  assert.equal(error, null)
})

test('가격 인하 경매에 최저가가 없으면 막는다', () => {
  // given
  const fields = downAuction({ minimumPrice: '' })

  // when
  const error = validateAuctionFields(fields)

  // then
  assert.equal(error, ERROR_MESSAGE.invalidMinimumPrice)
})

test('최저가가 시작가보다 낮지 않으면 막는다', () => {
  // given
  const sameAsStartPrice = downAuction({ startPrice: '100000', minimumPrice: '100000' })
  const higherThanStartPrice = downAuction({ startPrice: '100000', minimumPrice: '200000' })

  // when
  const errors = [sameAsStartPrice, higherThanStartPrice].map(validateAuctionFields)

  // then
  assert.deepEqual(errors, [
    ERROR_MESSAGE.minimumPriceMustBeLowerThanStartPrice,
    ERROR_MESSAGE.minimumPriceMustBeLowerThanStartPrice,
  ])
})

test('인하 금액이 0 이하면 가격이 내려가지 않으므로 막는다', () => {
  // given
  const zero = downAuction({ dropPrice: '0' })
  const negative = downAuction({ dropPrice: '-1000' })

  // when
  const errors = [zero, negative].map(validateAuctionFields)

  // then
  assert.deepEqual(errors, [ERROR_MESSAGE.invalidDropPrice, ERROR_MESSAGE.invalidDropPrice])
})

/* 인하 주기는 백엔드 enum과 1:1이라 목록에 없는 값은 저장 단계에서 깨진다. */
test('정해진 목록에 없는 인하 주기는 막는다', () => {
  // given
  const fields = downAuction({ priceDropInterval: '7' })

  // when
  const error = validateAuctionFields(fields)

  // then
  assert.equal(error, ERROR_MESSAGE.invalidPriceDropInterval)
})

/* 가격 인하 경매는 최저가에 닿을 때까지 값이 내려가므로 즉시 구매가를 쓰지 않는다. */
test('가격 인하 경매는 즉시 구매가를 검사하지 않는다', () => {
  // given
  const fields = downAuction({ buyNowPrice: '1' })

  // when
  const error = validateAuctionFields(fields)

  // then
  assert.equal(error, null)
})
