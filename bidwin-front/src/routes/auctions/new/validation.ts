import { CONTACT_MAX_LENGTH, ERROR_MESSAGE, MIN_PRICE, TITLE_MAX_LENGTH } from './constants'

/* 백엔드 AuctionCreateRequest.contact와 같은 기준(하이픈 없는 휴대폰 번호). */
const CONTACT_PATTERN = /^01[016789]\d{7,8}$/

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
  if (startPrice % MIN_PRICE !== 0) return ERROR_MESSAGE.invalidStartPriceUnit

  if (auctionType === 'UP') {
    const buyNowPrice = toPositiveNumber(fields.buyNowPrice)
    if (fields.buyNowPrice.trim() !== '') {
      if (buyNowPrice === null || buyNowPrice < MIN_PRICE) return ERROR_MESSAGE.invalidBuyNowPrice
      if (buyNowPrice <= startPrice) return ERROR_MESSAGE.buyNowPriceMustExceedStartPrice
    }
    return null
  }

  const minimumPrice = toPositiveNumber(fields.minimumPrice)
  if (minimumPrice === null || minimumPrice < MIN_PRICE) return ERROR_MESSAGE.invalidMinimumPrice
  if (minimumPrice >= startPrice) return ERROR_MESSAGE.minimumPriceMustBeLowerThanStartPrice

  const dropPrice = toPositiveNumber(fields.dropPrice)
  if (dropPrice === null || dropPrice <= 0) return ERROR_MESSAGE.invalidDropPrice

  const priceDropInterval = toPositiveNumber(fields.priceDropInterval)
  if (priceDropInterval === null || priceDropInterval <= 0) {
    return ERROR_MESSAGE.invalidPriceDropInterval
  }

  return null
}

export { validateAuctionFields }
export type { AuctionFormFields }
