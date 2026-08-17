/*
 * 경매 등록 폼 상수.
 * 길이·용량 제한은 백엔드 Auction 엔티티, AuctionImagePresignRequest 검증값과 같게 맞춘다.
 */

import { MAX_PRICE_EXCLUSIVE } from '../../../lib/auctionPrice'
import { AUCTION_CATEGORY_OPTIONS } from '../../../lib/auctionCategory'

export const TITLE_MAX_LENGTH = 30
export const CONTACT_MAX_LENGTH = 100

export const MAX_IMAGE_COUNT = 10
export const MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024
export const ALLOWED_IMAGE_CONTENT_TYPES = ['image/jpeg', 'image/png', 'image/webp']

/* 시작가·최저가·즉시 구매가에 공통으로 적용하는 최소 금액. */
export const MIN_PRICE = 1000

/* 시작가·즉시 구매가의 상한. 입찰가·즉시구매가에 적용되는 배타적 상한(1000억원 미만)과 맞춰,
 * 등록만 되고 아무도 살 수 없는 가격(정확히 1000억원)을 막는다. */
export const MAX_PRICE = MAX_PRICE_EXCLUSIVE - 1_000

/* 백엔드 AuctionCategory enum과 1:1 대응한다. */
export const CATEGORY_OPTIONS = AUCTION_CATEGORY_OPTIONS

export const AUCTION_TYPE_OPTIONS = [
  { value: 'DOWN', label: '가격 인하 (급처 타임어택)' },
  { value: 'UP', label: '경쟁 입찰 (가격 상승)' },
] as const

/* 백엔드 TradeType enum과 1:1 대응. 직거래를 선택해도 위치 입력은 받지 않고, 낙찰 후 당사자끼리 협의한다. */
export const TRADE_TYPE_OPTIONS = [
  { value: 'DELIVERY', label: '택배' },
  { value: 'DIRECT', label: '직거래' },
] as const

export const AUCTION_DURATION_OPTIONS = [
  { value: '6', label: '6분' },
  { value: '30', label: '30분' },
  { value: '60', label: '1시간' },
  { value: '180', label: '3시간' },
  { value: '360', label: '6시간' },
] as const

/* 백엔드 PriceDropInterval enum(1/3/5/10분)과 1:1 대응. */
export const PRICE_DROP_INTERVAL_OPTIONS = [
  { value: '1', label: '1분' },
  { value: '3', label: '3분' },
  { value: '5', label: '5분' },
  { value: '10', label: '10분' },
] as const

export const TEXT = {
  pageTitle: '경매 등록',
  pageSubtitle: '급처 물품 정보를 입력하고 사진을 올려 경매를 시작해보세요.',

  titleLabel: '제목',
  titlePlaceholder: `${TITLE_MAX_LENGTH}자 이내로 입력하세요`,
  descriptionLabel: '상품 설명',
  descriptionPlaceholder: '상품 상태, 사용 기간 등을 자세히 적어주세요',
  categoryLabel: '카테고리',
  categoryPlaceholder: '카테고리를 선택하세요',
  contactLabel: '연락처',
  contactPlaceholder: '휴대폰 번호(01012345678) 또는 오픈채팅방 링크(https://...)를 입력하세요',
  durationLabel: '경매 진행 시간',
  auctionTypeLabel: '경매 방식',
  tradeTypeLabel: '거래 방식',

  imagesLabel: '상품 이미지',
  imagesHelper: `최대 ${MAX_IMAGE_COUNT}장, 장당 10MB 이하 (JPEG/PNG/WEBP)`,

  startPriceLabel: '시작가',
  buyNowPriceLabel: '즉시 구매가 (선택)',
  minimumPriceLabel: '최저가',
  dropPriceLabel: '인하 금액',
  priceDropIntervalLabel: '인하 주기',

  submit: '경매 등록하기',
  submitting: '등록 처리 중…',

  completeTitle: '경매가 등록됐어요',
  completeDescription: '등록한 경매는 경매 목록과 마이페이지에서 확인할 수 있어요.',
  goToList: '경매 목록으로 이동',
  goToHome: '홈으로',

  submitSuccessToast: '경매를 등록했어요.',
} as const

export const ERROR_MESSAGE = {
  emptyRequiredField: '제목, 설명, 카테고리, 연락처를 모두 입력해주세요.',
  titleTooLong: `제목은 ${TITLE_MAX_LENGTH}자 이하로 입력해주세요.`,
  contactTooLong: `연락처는 ${CONTACT_MAX_LENGTH}자 이하로 입력해주세요.`,
  invalidContact: '연락처는 하이픈 없는 휴대폰 번호 또는 http(s)로 시작하는 링크로 입력해주세요.',
  invalidStartPrice: `시작가는 ${MIN_PRICE.toLocaleString('ko-KR')}원 이상으로 입력해주세요.`,
  invalidStartPriceUnit: `시작가는 ${MIN_PRICE.toLocaleString('ko-KR')}원 단위로 입력해주세요.`,
  startPriceTooHigh: `시작가는 ${MAX_PRICE.toLocaleString('ko-KR')}원 이하로 입력해주세요.`,
  invalidBuyNowPrice: `즉시 구매가는 ${MIN_PRICE.toLocaleString('ko-KR')}원 이상으로 입력해주세요.`,
  buyNowPriceTooHigh: `즉시 구매가는 ${MAX_PRICE.toLocaleString('ko-KR')}원 이하로 입력해주세요.`,
  buyNowPriceMustExceedStartPrice: '즉시 구매가는 시작가보다 높아야 해요.',
  invalidMinimumPrice: `최저가는 ${MIN_PRICE.toLocaleString('ko-KR')}원 이상으로 입력해주세요.`,
  minimumPriceMustBeLowerThanStartPrice: '최저가는 시작가보다 낮아야 해요.',
  invalidDropPrice: '인하 금액은 0보다 큰 숫자로 입력해주세요.',
  invalidPriceDropInterval: '인하 주기는 1분/3분/5분/10분 중에서 선택해주세요.',
  noImages: '상품 이미지를 1장 이상 올려주세요.',
  imagesUploading: '이미지 업로드가 끝날 때까지 기다려주세요.',
  imageUploadFailed: '이미지 업로드에 실패했어요. 다시 시도해주세요.',
  imageTooLarge: '이미지는 10MB 이하만 올릴 수 있어요.',
  unsupportedImageType: 'JPEG, PNG, WEBP 형식만 올릴 수 있어요.',
  tooManyImages: `이미지는 최대 ${MAX_IMAGE_COUNT}장까지 올릴 수 있어요.`,
  draftInitFailed: '이미지 업로드 준비에 실패했어요. 새로고침 후 다시 시도해주세요.',
} as const
