export const AUCTION_CATEGORY_CODES = [
  'HOUSEHOLD',
  'FOOD',
  'FURNITURE',
  'ELECTRONICS',
  'FASHION',
  'SPORTS',
  'HOBBY',
  'BOOK',
  'OTHER',
] as const

export type AuctionCategory = (typeof AUCTION_CATEGORY_CODES)[number]

export const AUCTION_CATEGORY_LABELS: Record<AuctionCategory, string> = {
  HOUSEHOLD: '생활용품',
  FOOD: '먹거리',
  FURNITURE: '가구',
  ELECTRONICS: '디지털/가전',
  FASHION: '패션/잡화',
  SPORTS: '스포츠/레저',
  HOBBY: '취미/수집',
  BOOK: '도서/문구',
  OTHER: '기타',
}

export const AUCTION_CATEGORY_OPTIONS = AUCTION_CATEGORY_CODES.map((value) => ({
  value,
  label: AUCTION_CATEGORY_LABELS[value],
}))
