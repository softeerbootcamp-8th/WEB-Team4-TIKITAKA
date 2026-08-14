import {
  CONTACT_MAX_LENGTH,
  ERROR_MESSAGE,
  MAX_PRICE,
  MIN_PRICE,
  PRICE_DROP_INTERVAL_OPTIONS,
  TITLE_MAX_LENGTH,
} from './constants'

/* 백엔드 AuctionCreateRequest.contact와 같은 기준.
 * 오픈채팅방 링크 등으로 연락받는 판매자도 있어 휴대폰 번호 또는 http(s) 링크를 모두 허용한다. */
const CONTACT_PATTERN = /^(01[016789]\d{7,8}|https?:\/\/\S+)$/

interface AuctionFormFields {
  title: string
  description: string
  category: string
  contact: string
  auctionType: 'UP' | 'DOWN'
  tradeType: 'DELIVERY' | 'DIRECT'
  startPrice: string
  buyNowPrice: string
  minimumPrice: string
  dropPrice: string
  priceDropInterval: string
}

function toPositiveNumber(value: string) {
  if (value.trim() === '') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function validateAuctionFields(fields: AuctionFormFields) {
  const { title, description, category, contact, auctionType } = fields

  if (!title || !description || !category || !contact) {
    return ERROR_MESSAGE.emptyRequiredField
  }
  if (title.length > TITLE_MAX_LENGTH) return ERROR_MESSAGE.titleTooLong
  if (contact.length > CONTACT_MAX_LENGTH) return ERROR_MESSAGE.contactTooLong
  if (!CONTACT_PATTERN.test(contact)) return ERROR_MESSAGE.invalidContact

  const startPrice = toPositiveNumber(fields.startPrice)
  if (startPrice === null || startPrice < MIN_PRICE) return ERROR_MESSAGE.invalidStartPrice
  if (startPrice > MAX_PRICE) return ERROR_MESSAGE.startPriceTooHigh
  if (startPrice % MIN_PRICE !== 0) return ERROR_MESSAGE.invalidStartPriceUnit

  if (auctionType === 'UP') {
    const buyNowPrice = toPositiveNumber(fields.buyNowPrice)
    if (fields.buyNowPrice.trim() !== '') {
      if (buyNowPrice === null || buyNowPrice < MIN_PRICE) return ERROR_MESSAGE.invalidBuyNowPrice
      if (buyNowPrice > MAX_PRICE) return ERROR_MESSAGE.buyNowPriceTooHigh
      if (buyNowPrice <= startPrice) return ERROR_MESSAGE.buyNowPriceMustExceedStartPrice
    }
    return null
  }

  const minimumPrice = toPositiveNumber(fields.minimumPrice)
  if (minimumPrice === null || minimumPrice < MIN_PRICE) return ERROR_MESSAGE.invalidMinimumPrice
  if (minimumPrice >= startPrice) return ERROR_MESSAGE.minimumPriceMustBeLowerThanStartPrice

  const dropPrice = toPositiveNumber(fields.dropPrice)
  if (dropPrice === null || dropPrice <= 0) return ERROR_MESSAGE.invalidDropPrice

  const isAllowedPriceDropInterval = PRICE_DROP_INTERVAL_OPTIONS.some(
    (option) => option.value === fields.priceDropInterval,
  )
  if (!isAllowedPriceDropInterval) {
    return ERROR_MESSAGE.invalidPriceDropInterval
  }

  return null
}

export { validateAuctionFields }
export type { AuctionFormFields }
