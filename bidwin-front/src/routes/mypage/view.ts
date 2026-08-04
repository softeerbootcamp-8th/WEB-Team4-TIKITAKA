import type { BadgeTone } from '../../components/ui/Badge'
import type { AuctionType } from '../auctions/types'
import {
  BUYING_STATUS_LABEL,
  BUYING_STATUS_TONE,
  SELLING_STATUS_LABEL,
  SELLING_STATUS_TONE,
} from './constants'
import type { BuyingItem, SellingItem } from './types'

/*
 * 판매 물품과 구매 물품은 상태 값만 다르고 카드 생김새는 같다.
 * 카드가 상태 종류마다 분기하지 않도록, 여기서 한 번 같은 모양으로 눕혀서 넘긴다.
 */
export interface ItemCardModel {
  auctionId: number
  title: string
  thumbnailUrl?: string
  auctionType: AuctionType
  startPrice: number
  price: number
  statusLabel: string
  statusTone: BadgeTone
  /** 값이 확정됐는지. 가격 라벨을 "현재가"와 "거래가"로 가른다. */
  isSettled: boolean
  /** 아직 손이 가야 하는 건인지. 미리보기에서 이런 물품을 앞으로 당긴다. */
  isOngoing: boolean
}

/** 진행 중인 물품을 앞으로 당긴다. 같은 무리 안에서는 받은 순서를 그대로 둔다. */
export function ongoingFirst(items: ItemCardModel[]): ItemCardModel[] {
  return [...items].sort((a, b) => Number(b.isOngoing) - Number(a.isOngoing))
}

export function toSellingCard(item: SellingItem): ItemCardModel {
  return {
    auctionId: item.auctionId,
    title: item.title,
    thumbnailUrl: item.thumbnailUrl,
    auctionType: item.auctionType,
    startPrice: item.startPrice,
    price: item.price,
    statusLabel: SELLING_STATUS_LABEL[item.status],
    statusTone: SELLING_STATUS_TONE[item.status],
    isSettled: item.status !== 'ON_SALE',
    isOngoing: item.status === 'ON_SALE',
  }
}

export function toBuyingCard(item: BuyingItem): ItemCardModel {
  return {
    auctionId: item.auctionId,
    title: item.title,
    thumbnailUrl: item.thumbnailUrl,
    auctionType: item.auctionType,
    startPrice: item.startPrice,
    price: item.price,
    statusLabel: BUYING_STATUS_LABEL[item.status],
    statusTone: BUYING_STATUS_TONE[item.status],
    /* 구매 물품은 낙찰된 시점에 값이 정해지므로 어느 상태든 거래가로 부른다. */
    isSettled: true,
    isOngoing: item.status !== 'DONE',
  }
}
