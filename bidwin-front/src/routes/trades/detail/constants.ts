/*
 * 거래 화면에서 쓰는 문구·경로·상태 매핑. 화면 컴포넌트에 문자열을 하드코딩하지 않는다.
 */
import type { BadgeTone } from '../../../components/ui/Badge'
import type { TradeStatus } from '../../../lib/api/mypage'

export const MYPAGE_PATH = '/mypage'

/** 다음에 움직여야 할 쪽을 기준으로 상태를 읽는다(대기 중인 확정이 무엇인지). */
export const TRADE_STATUS_LABEL: Record<TradeStatus, string> = {
  WAITING_CONFIRM: '구매확정 대기',
  CONFIRMED: '판매확정 대기',
  COMPLETED: '거래 완료',
  BUYER_FAILED: '구매자 미확정',
  SELLER_FAILED: '판매자 미확정',
}

export const TRADE_STATUS_TONE: Record<TradeStatus, BadgeTone> = {
  WAITING_CONFIRM: 'primary',
  CONFIRMED: 'neutral',
  COMPLETED: 'success',
  BUYER_FAILED: 'danger',
  SELLER_FAILED: 'danger',
}

export const TRADE_DETAIL_TEXT = {
  back: '마이페이지로',
  priceLabel: '거래가',

  loading: '거래 정보를 불러오는 중이에요.',
  retry: '다시 시도',
  errorTitle: '거래 정보를 불러오지 못했어요',
  errorDescription: '잠시 후 다시 시도해주세요.',
  notFoundTitle: '거래를 찾을 수 없어요',
  notFoundDescription: '이미 종료됐거나 존재하지 않는 거래예요.',
  accessDeniedTitle: '접근할 수 없는 거래예요',
  accessDeniedDescription: '본인이 참여한 거래만 볼 수 있어요.',

  // WAITING_CONFIRM
  buyerActionTitle: '구매를 확정해 주세요',
  buyerActionDescription: '구매확정을 하면 결제가 마무리되고, 판매자 연락처가 공개돼요.',
  confirmPurchase: '구매확정',
  waitingBuyerTitle: '구매자의 확정을 기다리고 있어요',
  waitingBuyerDescription: '구매자가 구매확정을 하면 다음 단계로 넘어가요.',

  // CONFIRMED
  contactTitle: '판매자 연락처',
  contactDescription: '연락처로 판매자와 연락해 물건 받을 일정을 잡아 주세요.',
  contactUnavailable: '연락처를 아직 불러오지 못했어요. 잠시 후 다시 확인해 주세요.',
  buyerWaitingSellerTitle: '판매자의 판매확정을 기다리고 있어요',
  buyerWaitingSellerDescription: '판매자가 물건을 건네고 판매확정을 하면 거래가 완료돼요.',
  sellerActionTitle: '판매를 확정해 주세요',
  sellerActionDescription: '물건을 건넨 뒤 판매확정을 하면 거래가 완료되고 정산돼요.',
  confirmSale: '판매확정',

  // COMPLETED / 종료
  completedTitle: '거래가 완료되었어요',
  completedDescription: '거래가 성공적으로 마무리됐어요.',
  endedTitle: '종료된 거래예요',
  endedDescription: '더 이상 진행할 수 없는 거래예요.',

  // toast
  purchaseConfirmed: '구매를 확정했어요.',
  saleConfirmed: '판매를 확정했어요. 거래가 완료됐어요.',
} as const
