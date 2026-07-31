import { CONTACT_MAX_LENGTH, ERROR_MESSAGE, MIN_PRICE, TITLE_MAX_LENGTH } from './constants'

interface AuctionFormFields {
  title: string
  description: string
  category: string
  contact: string
  auctionType: 'UP' | 'DOWN'
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

  const startPrice = toPositiveNumber(fields.startPrice)
  if (startPrice === null || startPrice < MIN_PRICE) return ERROR_MESSAGE.invalidStartPrice

  if (auctionType === 'UP') {
    const buyNowPrice = toPositiveNumber(fields.buyNowPrice)
    if (fields.buyNowPrice.trim() !== '' && (buyNowPrice === null || buyNowPrice < MIN_PRICE)) {
      return ERROR_MESSAGE.invalidBuyNowPrice
    }
    return null
  }

  const minimumPrice = toPositiveNumber(fields.minimumPrice)
  if (minimumPrice === null || minimumPrice < MIN_PRICE) return ERROR_MESSAGE.invalidMinimumPrice

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
