export type {
  AuctionStatus,
  AuctionSummary,
  AuctionType,
} from '../../lib/api/auctions'
import type { AuctionType } from '../../lib/api/auctions'

/** 목록 화면에서 고르는 경매 종류. 'ALL'은 두 종류를 모두 본다는 뜻이다. */
export type AuctionTypeFilter = AuctionType | 'ALL'
