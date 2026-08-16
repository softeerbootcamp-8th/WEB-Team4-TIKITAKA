/*
 * 마이페이지에서 반복해서 쓰이는 숫자·문구.
 * 화면 문구나 미리보기 개수를 바꿀 일이 생기면 컴포넌트가 아니라 이 파일만 고친다.
 */
import type { BadgeTone } from '../../components/ui/Badge'
import {
  NICKNAME_MAX_LENGTH,
  NICKNAME_MIN_LENGTH,
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
} from '../../lib/auth/validation'
import type { ActiveTrade, BuyingStatus, SellingStatus, TradeRole } from './types'

/** 내역 페이지에서 어떤 탭을 열지 정하는 쿼리 스트링 키. 마이페이지와 내역 페이지가 공유한다. */
export const HISTORY_TAB_PARAM = 'tab'

export const HISTORY_TAB = {
  bidding: 'bidding',
  won: 'won',
  purchase: 'purchase',
  selling: 'selling',
  deposit: 'deposit',
} as const

export type HistoryTabKey = (typeof HISTORY_TAB)[keyof typeof HISTORY_TAB]

export const DEFAULT_HISTORY_TAB: HistoryTabKey = HISTORY_TAB.bidding

/** 내역 페이지의 특정 탭으로 바로 들어가는 링크. 뒤로 가기로 원래 탭에 돌아올 수 있다. */
export function historyPath(tab: HistoryTabKey) {
  return `/mypage/history?${HISTORY_TAB_PARAM}=${tab}`
}

/** 섹션마다 미리 보여줄 물품 수. 나머지는 "전체 보기"로 내역 페이지에 넘긴다. */
export const ITEM_PREVIEW_LIMIT = 3
/*
 * 드로어가 열리고 닫히는 데 걸리는 시간(ms).
 * 마크업의 duration-300과 같은 값이어야 닫히는 애니메이션이 끝난 뒤 언마운트된다. 둘을 함께 바꾼다.
 */
export const DRAWER_TRANSITION_MS = 300

const BYTES_PER_MB = 1024 * 1024
export const PROFILE_IMAGE_MAX_MB = 5
export const PROFILE_IMAGE_MAX_BYTES = PROFILE_IMAGE_MAX_MB * BYTES_PER_MB
export const PROFILE_IMAGE_ACCEPT = 'image/jpeg,image/png,image/webp'

export const MYPAGE_TEXT = {
  title: '마이페이지',
} as const

export const ACTIVE_TRADE_TEXT = {
  title: (count: number) => `진행 중인 거래가 ${count}건 있어요`,
  description: '남은 단계를 마치면 거래가 완료돼요.',
  viewAll: '전체 보기',
} as const

export const TRADE_ROLE_LABEL: Record<TradeRole, string> = {
  BUYER: '구매',
  SELLER: '판매',
}

/** 역할에 따라 "내가" 해야 할 일이 달라진다. 파는 쪽은 기다리는 단계가 섞여 있다. */
export const TRADE_NEXT_ACTION_LABEL: Record<
  ActiveTrade['status'],
  Record<TradeRole, string>
> = {
  PAYMENT_PENDING: { BUYER: '결제하기', SELLER: '결제 기다리는 중' },
  IN_PROGRESS: { BUYER: '거래 일정 조율', SELLER: '거래 일정 조율' },
}

/*
 * 지금 움직여야 하는 쪽. 파는 쪽은 결제·수령을 기다리기만 하는 단계가 있어서,
 * 내 차례일 때만 강조색을 준다(모든 카드가 빨갛게 보이면 급한 게 뭔지 알 수 없다).
 */
export const TRADE_MY_TURN_ROLES: Record<ActiveTrade['status'], readonly TradeRole[]> = {
  PAYMENT_PENDING: ['BUYER'],
  IN_PROGRESS: ['BUYER', 'SELLER'],
}

export const PROFILE_TEXT = {
  avatarAlt: '프로필 이미지',
  sellCountSuffix: '회 판매',
  joinCountSuffix: '회 경매 참여',
  joinedSuffix: ' 가입',
  manage: '내 정보 관리',
} as const

export const DEPOSIT_TEXT = {
  title: '보증금',
  balanceLabel: '잔액',
  inUseLabel: '보증금으로 사용 중',
  availableLabel: '사용 가능',
  notice: '입찰할 때 보증금이 자동으로 잡히고, 거래가 끝나면 사용 가능 금액으로 돌아와요.',
  history: '보증금 내역',
} as const

/* 실제 결제 연동 전, 테스트를 위해 잔액을 바로 채워주는 충전 기능. */
export const DEPOSIT_CHARGE_TEXT = {
  action: '충전하기',
  modalTitle: '보증금 충전',
  modalDescription: '실제 결제 없이 테스트용으로 잔액을 채워요.',
  presetLabel: '자주 찾는 금액',
  presets: [
    { value: '1000000', label: '100만원' },
    { value: '5000000', label: '500만원' },
    { value: '10000000', label: '1,000만원' },
  ] as const,
  amountLabel: '충전 금액',
  submit: '충전하기',
  submitting: '충전 중…',
  done: '포인트를 충전했어요.',
  amountRequired: '충전할 금액을 입력해주세요.',
  amountUnitInvalid: '1,000원 단위로 입력해주세요.',
  amountTooLarge: '한 번에 최대 1억 원까지 충전할 수 있어요.',
} as const

export const SELLING_SECTION_TEXT = {
  title: '판매 물품',
  viewAll: '판매 내역 전체 보기',
  empty: '판매 중인 물품이 없어요.',
  emptyAction: '판매하기',
} as const

export const BUYING_SECTION_TEXT = {
  title: '구매 물품',
  viewAll: '구매 내역 전체 보기',
  empty: '구매한 물품이 없어요.',
  emptyAction: '경매 둘러보기',
} as const

/** 진행 중인 물품은 값이 더 움직이므로 "현재가", 끝난 물품은 확정된 "거래가"로 부른다. */
export const ITEM_CARD_TEXT = {
  startPriceLabel: '시작가',
  currentPriceLabel: '현재가',
  finalPriceLabel: '거래가',
} as const

export const SELLING_STATUS_LABEL: Record<SellingStatus, string> = {
  ON_SALE: '판매 중',
  SOLD: '낙찰 완료',
  FAILED: '유찰',
}

export const SELLING_STATUS_TONE: Record<SellingStatus, BadgeTone> = {
  ON_SALE: 'dark',
  SOLD: 'success',
  FAILED: 'muted',
}

export const BUYING_STATUS_LABEL: Record<BuyingStatus, string> = {
  PAYMENT_PENDING: '결제 대기',
  IN_PROGRESS: '거래 중',
  DONE: '거래 완료',
}

export const BUYING_STATUS_TONE: Record<BuyingStatus, BadgeTone> = {
  PAYMENT_PENDING: 'primary',
  IN_PROGRESS: 'neutral',
  DONE: 'success',
}

export const SETTINGS_TEXT = {
  title: '설정',
  purchase: '구매 내역',
  bidding: '입찰 내역',
  won: '낙찰 내역',
  myInfo: '내 정보 관리',
} as const

export const MY_INFO_TEXT = {
  title: '내 정보',
  description: '프로필과 로그인 정보를 관리해요.',
  close: '내 정보 관리 닫기',

  imageSectionTitle: '프로필 이미지',
  imageChange: '이미지 변경',
  imageReset: '기본 이미지로',
  imageHint: `JPG·PNG 등 이미지 파일, ${PROFILE_IMAGE_MAX_MB}MB 이하`,
  imageTooLarge: `이미지는 ${PROFILE_IMAGE_MAX_MB}MB 이하만 올릴 수 있어요.`,
  imageNotSupported: '이미지 파일만 올릴 수 있어요.',
  imageUploadFailed: '프로필 이미지 업로드에 실패했어요. 다시 시도해주세요.',
  imageSelected: '프로필 이미지를 바꿨어요.',
  imageResetDone: '기본 이미지로 되돌렸어요.',

  nicknameSectionTitle: '닉네임',
  nicknameLabel: '닉네임',
  nicknameHint: `${NICKNAME_MIN_LENGTH}~${NICKNAME_MAX_LENGTH}자로 입력해주세요.`,
  nicknameSubmit: '닉네임 변경',
  nicknameUnchanged: '지금 쓰는 닉네임과 같아요.',
  nicknameDone: '닉네임을 바꿨어요.',

  passwordSectionTitle: '비밀번호 변경',
  currentPasswordLabel: '현재 비밀번호',
  newPasswordLabel: '새 비밀번호',
  newPasswordConfirmLabel: '새 비밀번호 확인',
  newPasswordHint: `${PASSWORD_MIN_LENGTH}~${PASSWORD_MAX_LENGTH}자, 특수문자 1개 이상 포함`,
  passwordSubmit: '비밀번호 변경',
  currentPasswordRequired: '현재 비밀번호를 입력해주세요.',
  passwordSameAsCurrent: '현재 비밀번호와 다른 비밀번호로 바꿔주세요.',
  passwordDone: '비밀번호를 바꿨어요. 다음 로그인부터 새 비밀번호를 써주세요.',

  leave: '회원 탈퇴',
} as const

export const LEAVE_MODAL_TEXT = {
  title: '정말 탈퇴하시겠어요?',
  description: '탈퇴하면 되돌릴 수 없어요.',
  losses: [
    '입찰·낙찰·판매 내역이 모두 사라져요.',
    '남은 보증금은 환급되지 않아요.',
    '같은 이메일로는 다시 가입할 수 없어요.',
  ],
  confirmLabel: '위 내용을 모두 확인했어요.',
  cancel: '취소',
  submit: '탈퇴하기',
  /* 거래가 남아 있으면 탈퇴를 막는다. 상대방이 있는 거래를 일방적으로 끊을 수 없다. */
  blockedByTrade: (count: number) => `진행 중인 거래 ${count}건을 먼저 마쳐야 탈퇴할 수 있어요.`,
  blockedByDeposit: '입찰에 묶인 보증금이 풀린 뒤에 탈퇴할 수 있어요.',
  unavailable: '회원 탈퇴 API가 준비되지 않아 현재 탈퇴할 수 없어요.',
} as const
