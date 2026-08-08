import { getJson, postJson } from './client'
import type { ApiResult } from './client'
import type { AuctionType } from './auctions'
import type { TradeRole, TradeStatus } from './mypage'

const TRADES_API_PATH = '/api/v1/trades'

/*
 * 거래 확정 API는 요청 본문이 없다. 다만 공통 클라이언트가 항상 Content-Type을 붙이므로
 * 빈 JSON 객체를 보내 서버가 본문 없이도 처리하도록 한다.
 */
type EmptyRequestBody = Record<string, never>
const EMPTY_BODY: EmptyRequestBody = {}

export interface TradeDetail {
  tradeId: number
  auctionId: number
  title: string
  thumbnailUrl: string | null
  auctionType: AuctionType
  status: TradeStatus
  role: TradeRole
  finalPrice: number
  purchasedAt: number
  /* 규칙상 CONFIRMED 이후 구매자에게만 채워진다. 그 외에는 항상 null이다. */
  sellerContact: string | null
}

export interface TradeConfirmationResponse {
  tradeId: number
  auctionId: number
  status: TradeStatus
  finalPrice: number
}

export function requestTradeDetail(
  tradeId: number,
  signal?: AbortSignal,
): Promise<ApiResult<TradeDetail>> {
  return getJson<TradeDetail>(`${TRADES_API_PATH}/${tradeId}`, signal)
}

export function requestBuyerConfirmation(
  tradeId: number,
): Promise<ApiResult<TradeConfirmationResponse>> {
  return postJson<TradeConfirmationResponse, EmptyRequestBody>(
    `${TRADES_API_PATH}/${tradeId}/buyer-confirmation`,
    EMPTY_BODY,
  )
}

export function requestSellerConfirmation(
  tradeId: number,
): Promise<ApiResult<TradeConfirmationResponse>> {
  return postJson<TradeConfirmationResponse, EmptyRequestBody>(
    `${TRADES_API_PATH}/${tradeId}/seller-confirmation`,
    EMPTY_BODY,
  )
}
