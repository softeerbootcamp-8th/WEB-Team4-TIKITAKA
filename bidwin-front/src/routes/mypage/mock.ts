import type { ActiveTrade, BuyingItem, DepositAccount, MyProfile, SellingItem } from './types'

/*
 * 마이페이지 API가 아직 없어서 임시 데이터를 쓴다.
 * 실제 연동 시 이 파일 대신 fetch 결과를 page.tsx에 넘기면 되고, 나머지 코드는 그대로다.
 */

const NOW = Date.now()
const MINUTE_MS = 60 * 1000
const HOUR_MS = 60 * MINUTE_MS

/** 프로필의 가입일. 화면에 "2026년 7월 19일 가입"으로 나온다. */
const JOINED_AT = new Date(2026, 6, 19).getTime()

export const MOCK_PROFILE: MyProfile = {
  nickname: '급처하는근성',
  joinedAt: JOINED_AT,
  sellCount: 17,
  auctionJoinCount: 20,
}

export const MOCK_DEPOSIT: DepositAccount = {
  balance: 60000,
  inUse: 16000,
}

/*
 * 아직 끝나지 않은 거래. 사는 쪽·파는 쪽이 섞여 있고, 기한이 가까운 순으로 배너에 나온다.
 * 이 배열이 비면 배너 자체가 그려지지 않는다.
 */
export const MOCK_ACTIVE_TRADES: ActiveTrade[] = [
  {
    tradeId: 1,
    auctionId: 90,
    title: '닌텐도 스위치 OLED + 게임 3종',
    role: 'BUYER',
    status: 'PAYMENT_PENDING',
    price: 265000,
  },
  {
    tradeId: 2,
    auctionId: 71,
    title: '애플워치 SE 40mm',
    role: 'SELLER',
    status: 'PAYMENT_PENDING',
    price: 180000,
  },
  {
    tradeId: 3,
    auctionId: 91,
    title: '캠핑 4인용 텐트 풀세트',
    role: 'BUYER',
    status: 'SHIPPING',
    price: 98000,
  },
  {
    tradeId: 4,
    auctionId: 73,
    title: '르크루제 무쇠 냄비 3종 세트',
    role: 'SELLER',
    status: 'IN_PROGRESS',
    price: 150000,
  },
]

export const MOCK_SELLING_ITEMS: SellingItem[] = [
  {
    auctionId: 201,
    title: '침대 급처합니다. 퀸 사이즈 프레임 + 매트리스',
    auctionType: 'DOWN',
    startPrice: 100000,
    price: 100000,
    status: 'ON_SALE',
    downPricing: {
      startPrice: 100000,
      minimumPrice: 30000,
      dropPrice: 5000,
      priceDropIntervalMs: HOUR_MS,
      startedAt: NOW - 11 * HOUR_MS,
    },
  },
  {
    auctionId: 202,
    title: '책상 급처합니다. 1400x700 원목',
    auctionType: 'DOWN',
    startPrice: 50000,
    price: 50000,
    status: 'ON_SALE',
    downPricing: {
      startPrice: 50000,
      minimumPrice: 20000,
      dropPrice: 5000,
      priceDropIntervalMs: HOUR_MS,
      startedAt: NOW - 4 * HOUR_MS,
    },
  },
  {
    auctionId: 71,
    title: '애플워치 SE 40mm',
    auctionType: 'UP',
    startPrice: 150000,
    price: 180000,
    status: 'SOLD',
  },
  {
    auctionId: 72,
    title: '무선 청소기 (충전 거치대 포함)',
    auctionType: 'UP',
    startPrice: 60000,
    price: 60000,
    status: 'FAILED',
  },
]

export const MOCK_BUYING_ITEMS: BuyingItem[] = [
  {
    auctionId: 90,
    title: '닌텐도 스위치 OLED + 게임 3종',
    auctionType: 'UP',
    startPrice: 200000,
    price: 265000,
    status: 'PAYMENT_PENDING',
  },
  {
    auctionId: 91,
    title: '캠핑 4인용 텐트 풀세트',
    auctionType: 'DOWN',
    startPrice: 140000,
    price: 98000,
    status: 'SHIPPING',
  },
  {
    auctionId: 92,
    title: '소니 WH-1000XM5 노이즈캔슬링 헤드폰',
    auctionType: 'UP',
    startPrice: 210000,
    price: 232000,
    status: 'DONE',
  },
]
