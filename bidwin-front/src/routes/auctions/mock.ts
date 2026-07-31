import type { AuctionSummary } from './types'

/*
 * 경매 목록 API가 아직 없어서 임시 데이터를 쓴다.
 * 실제 연동 시 이 파일 대신 fetch 결과를 page.tsx에 넘기면 되고, 나머지 코드는 그대로다.
 *
 * 페이지네이션을 눈으로 확인하려면 PAGE_SIZE(15)를 넘는 양이 필요해서 템플릿을 돌려
 * 만들어 낸다. 렌더링마다 값이 흔들리면 안 되므로 난수 대신 index 연산을 쓴다.
 */

const MOCK_COUNT = 48
const NOW = Date.now()

/** 급처 경매라 마감은 45초 ~ 58분 사이로만 흩뿌린다. */
const MIN_REMAINING_SECONDS = 45
const MAX_REMAINING_SPREAD_SECONDS = 3435
const MAX_LISTED_AGO_SECONDS = 72 * 60 * 60
/** 등록 시각을 흩뿌리는 보폭(초). MAX_LISTED_AGO_SECONDS와 서로소라 값이 골고루 퍼진다. */
const LISTED_AGO_STEP_SECONDS = 13817

interface AuctionTemplate {
  title: string
  category: string
  basePrice: number
  hashtags: string[]
}

const TEMPLATES: AuctionTemplate[] = [
  { title: '소니 WH-1000XM5 노이즈캔슬링 헤드폰', category: '디지털/가전', basePrice: 210000, hashtags: ['미개봉', '정품', '직거래'] },
  { title: '애플워치 울트라2 49mm 티타늄', category: '디지털/가전', basePrice: 480000, hashtags: ['미개봉', '애플케어', '보증서'] },
  { title: '캠핑 4인용 텐트 풀세트', category: '스포츠/레저', basePrice: 95000, hashtags: ['원터치', '방수', '수납가방'] },
  { title: '닌텐도 스위치 OLED + 게임 3종', category: '취미/게임', basePrice: 260000, hashtags: ['정품', '게임포함', '박스보관'] },
  { title: '르크루제 무쇠 냄비 3종 세트', category: '생활/주방', basePrice: 145000, hashtags: ['거의새것', '선물용', '정품'] },
  { title: '다이슨 에어랩 컴플리트 롱', category: '뷰티/미용', basePrice: 320000, hashtags: ['정품', '풀박스', '영수증'] },
  { title: '아이패드 프로 11 (M4) 256GB', category: '디지털/가전', basePrice: 890000, hashtags: ['애플펜슬', '미개봉', '와이파이'] },
  { title: '삼성 비스포크 4도어 냉장고', category: '생활/주방', basePrice: 720000, hashtags: ['직접수령', '이사정리', '설치협의'] },
  { title: '허먼밀러 에어론 리마스터드', category: '가구/인테리어', basePrice: 640000, hashtags: ['정품', '풀옵션', '사무실정리'] },
  { title: '루이비통 네버풀 MM 모노그램', category: '패션/잡화', basePrice: 1180000, hashtags: ['정품', '더스트백', '영수증'] },
  { title: '트렉 로드자전거 도마니 AL5', category: '스포츠/레저', basePrice: 540000, hashtags: ['입문용', '정비완료', '헬멧포함'] },
  { title: '캐논 EOS R6 마크2 바디', category: '디지털/가전', basePrice: 1650000, hashtags: ['셔터적음', '배터리2개', '보증기간'] },
]

const SELLERS = ['빠른정리', '이사가요', '오늘퇴근', '주말정리맨', '창고비움', '깔끔한거래']
const REGIONS = ['서울 강남', '서울 송파', '서울 마포', '경기 성남', '경기 수원', '부산 해운대']
const CONDITIONS = ['미개봉', '거의 새것', '사용감 적음', '사용감 있음']

/** 하향 경매가 시작가 대비 떨어진 비율(%) 후보. 인덱스로 골라 결정적으로 만든다. */
const DROP_RATES = [8, 14, 22, 31, 45]
/** 상향 경매에서 입찰 1회당 오르는 비율(%) */
const BID_STEP_RATE = 2

function roundToThousand(price: number) {
  return Math.round(price / 1000) * 1000
}

export const MOCK_AUCTIONS: AuctionSummary[] = Array.from({ length: MOCK_COUNT }, (_, index) => {
  const template = TEMPLATES[index % TEMPLATES.length]
  /* 같은 템플릿이 몇 번째로 반복되는지. 판매자·지역·입찰수를 이 값으로 흔들어 같은 카드가 겹쳐 보이지 않게 한다. */
  const variant = Math.floor(index / TEMPLATES.length)
  /* variant를 섞어 같은 상품이 반복될 때 상향/하향이 번갈아 나오게 한다. */
  const isDown = (index + variant) % 2 === 1
  const bidCount = isDown ? 0 : (index * 7 + variant * 5) % 24
  const dropRate = DROP_RATES[(index + variant) % DROP_RATES.length]

  const currentPrice = isDown
    ? roundToThousand(template.basePrice * (1 - dropRate / 100))
    : roundToThousand(template.basePrice * (1 + (bidCount * BID_STEP_RATE) / 100))

  return {
    auctionId: index + 1,
    title: template.title,
    auctionType: isDown ? 'DOWN' : 'UP',
    sellerName: SELLERS[(index + variant * 2) % SELLERS.length],
    category: template.category,
    region: REGIONS[(index + variant * 3) % REGIONS.length],
    condition: CONDITIONS[(index + variant) % CONDITIONS.length],
    hashtags: template.hashtags,
    currentPrice,
    startPrice: template.basePrice,
    bidCount,
    viewCount: 12 + ((index * 137 + variant * 29) % 420),
    deadline:
      NOW +
      (MIN_REMAINING_SECONDS + ((index * 271) % MAX_REMAINING_SPREAD_SECONDS)) * 1000,
    listedAt: NOW - ((index * LISTED_AGO_STEP_SECONDS) % MAX_LISTED_AGO_SECONDS) * 1000,
  }
})
