/** 상향 경매는 입찰이 붙을수록 가격이 오르고, 하향 경매는 시간이 갈수록 가격이 떨어진다. */
export type AuctionType = 'UP' | 'DOWN'

/** 목록 화면에서 고르는 경매 종류. 'ALL'은 두 종류를 모두 본다는 뜻이다. */
export type AuctionTypeFilter = AuctionType | 'ALL'

export interface AuctionSummary {
  auctionId: number
  title: string
  auctionType: AuctionType
  sellerName: string
  category: string
  region: string
  condition: string
  hashtags: string[]
  /** 상품 대표 이미지. API 연동 전이라 비어 있으면 카드가 자리표시자를 그린다. */
  thumbnailUrl?: string
  /** 지금 입찰(또는 즉시 구매)해야 하는 가격 */
  currentPrice: number
  /** 등록 당시 가격. 하향 경매에서 얼마나 떨어졌는지 보여주는 기준이 된다. */
  startPrice: number
  bidCount: number
  viewCount: number
  /** 마감 시각 (epoch ms) */
  deadline: number
  /** 등록 시각 (epoch ms) */
  listedAt: number
}
