import type { AuctionType } from '../auctions/types'

/** 거래에서 내가 맡은 쪽. 같은 카드를 사는 쪽·파는 쪽 양쪽에서 재사용한다. */
export type TradeRole = 'BUYER' | 'SELLER'

/** 낙찰 이후 거래 진행 상태. DONE 이전 단계는 모두 "아직 끝나지 않은 거래"다. */
export type TradeStatus = 'PAYMENT_PENDING' | 'IN_PROGRESS' | 'SHIPPING' | 'DONE'

/** 내가 올린 물품의 경매 상태 */
export type SellingStatus = 'ON_SALE' | 'SOLD' | 'FAILED'

/** 내가 사는 쪽인 물품의 거래 상태 */
export type BuyingStatus = 'PAYMENT_PENDING' | 'IN_PROGRESS' | 'SHIPPING' | 'DONE'

export interface MyProfile {
  nickname: string
  /** 비어 있으면 닉네임 첫 글자로 만든 기본 아바타를 그린다. */
  profileImageUrl?: string
  /** 가입 시각 (epoch ms) */
  joinedAt: number
  sellCount: number
  auctionJoinCount: number
}

export interface DepositAccount {
  /** 계정에 들어 있는 보증금 총액 */
  balance: number
  /** 진행 중인 입찰·거래에 묶여 있어 지금은 쓸 수 없는 금액 */
  inUse: number
}

/**
 * 아직 끝나지 않은 거래. 내가 사는 쪽이든 파는 쪽이든 상관없이,
 * 다음 할 일이 남아 있으면 마이페이지 맨 위 배너에 올라온다.
 */
export interface ActiveTrade {
  tradeId: number
  auctionId: number
  title: string
  thumbnailUrl?: string
  role: TradeRole
  status: Exclude<TradeStatus, 'DONE'>
  price: number
  /** 이번 단계를 끝내야 하는 기한 (epoch ms) */
  dueAt: number
}

/** 판매 물품·구매 물품 카드가 함께 쓰는 껍데기. status의 종류만 역할에 따라 갈린다. */
interface MyItemBase {
  auctionId: number
  title: string
  thumbnailUrl?: string
  auctionType: AuctionType
  /** 등록 당시 가격 */
  startPrice: number
  /** 진행 중이면 현재가, 끝났으면 최종 거래가 */
  price: number
}

export interface SellingItem extends MyItemBase {
  status: SellingStatus
}

export interface BuyingItem extends MyItemBase {
  status: BuyingStatus
}
