import {
  BadgeCheck,
  Clock,
  Flag,
  Heart,
  ImageOff,
  TrendingDown,
  Truck,
} from 'lucide-react'
import type { ChangeEvent } from 'react'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import Badge from '../../../components/ui/Badge'
import type { BadgeTone } from '../../../components/ui/Badge'
import Button from '../../../components/ui/Button'
import Card from '../../../components/ui/Card'
import TextInput from '../../../components/ui/TextInput'
import { useCountdown } from '../../../hooks/useCountdown'
import { useDownAuctionClock } from '../../../hooks/useDownAuctionClock'
import { useToast } from '../../../hooks/useToast'
import {
  getLiveAuctionPrice,
  getOrInitStartedAt,
  setLiveAuctionPrice,
} from '../../../lib/auctionLiveStore'
import { computeDropHistory } from '../../../lib/auctionPricing'
import { formatClock, formatTimeOfDay, formatWon } from '../../../lib/format'

/*
 * 경매 상세 API가 아직 없어서 임시 데이터를 쓴다 (경매 목록/상세/입찰/즉시구매 전부 미구현).
 * 실제 연동 시 이 배열을 fetch 결과로 바꾸면 된다 (예: src/routes/auctions/detail/api.ts).
 * 타입은 실제 백엔드 엔티티/enum(Auction, UpAuction, DownAuction, AuctionStatus 등)에 맞춰뒀다.
 * auctionId 1~8은 메인 페이지(src/routes/page.tsx) 목업과 같은 상품 — 제목/가격/하락
 * 파라미터를 반드시 맞춰서 화면 넘어갈 때 내용이 안 바뀌게 한다. 101/102는 화면 상태
 * 확인용(종료/유찰) 테스트 데이터라 메인 페이지엔 없다.
 */
const BID_INCREMENT_OPTIONS = [10000, 50000, 100000] as const
const DOWN_DROP_INTERVAL_MS = 15 * 1000

type AuctionCategory = 'HOUSEHOLD' | 'FOOD' | 'FURNITURE'
type AuctionStatus =
  | 'OPEN'
  | 'BID_ONGOING'
  | 'WINNER_DETERMINING'
  | 'COMPLETED'
  | 'UNSOLD'
  | 'CANCELED'

const CATEGORY_LABEL: Record<AuctionCategory, string> = {
  HOUSEHOLD: '생활용품',
  FOOD: '먹거리',
  FURNITURE: '가구',
}

const STATUS_LABEL: Record<AuctionStatus, string> = {
  OPEN: '대기중',
  BID_ONGOING: '진행중',
  WINNER_DETERMINING: '낙찰자 결정 중',
  COMPLETED: '거래 완료',
  UNSOLD: '유찰',
  CANCELED: '취소됨',
}

const STATUS_BADGE_TONE: Record<AuctionStatus, BadgeTone> = {
  OPEN: 'muted',
  BID_ONGOING: 'live',
  WINNER_DETERMINING: 'dark',
  COMPLETED: 'success',
  UNSOLD: 'muted',
  CANCELED: 'danger',
}

interface Seller {
  name: string
  verified: boolean
  dealCount: number
  rating: number
}

interface AuctionBase {
  auctionId: number
  title: string
  description: string
  category: AuctionCategory
  status: AuctionStatus
  images: string[]
  startPrice: number
  deposit: number
  seller: Seller
  viewCount: number
  deliveryNote: string
}

interface BidEntry {
  id: string
  bidder: string
  amount: number
  biddedAt: number
  isMe?: boolean
}

interface UpAuctionDetail extends AuctionBase {
  auctionType: 'UP'
  buyNowPrice: number
  currentPrice: number
  deadline: number
  bidLog: BidEntry[]
}

interface DownAuctionDetail extends AuctionBase {
  auctionType: 'DOWN'
  minimumPrice: number
  dropPrice: number
  priceDropIntervalMs: number
  startedAt: number
}

type AuctionDetail = UpAuctionDetail | DownAuctionDetail

const MOCK_AUCTIONS: AuctionDetail[] = [
  {
    auctionId: 1,
    title: '소니 WH-1000XM5 노이즈캔슬링 헤드폰 (미개봉)',
    description: '미개봉 새 상품입니다.\n구성품: 본체, 충전 케이블, 파우치, 보증서.',
    category: 'HOUSEHOLD',
    status: 'BID_ONGOING',
    images: [],
    startPrice: 180000,
    deposit: 30000,
    seller: { name: '급처직거래', verified: true, dealCount: 128, rating: 4.9 },
    viewCount: 214,
    deliveryNote: '직거래 또는 택배 거래 가능합니다. 직거래 희망 시 강남역 인근에서 만나요.',
    auctionType: 'UP',
    buyNowPrice: 320000,
    currentPrice: 210000,
    deadline: Date.now() + 8 * 60 * 1000,
    bidLog: [
      { id: 'b1', bidder: '민준**', amount: 190000, biddedAt: Date.now() - 3 * 60 * 1000 },
      { id: 'b2', bidder: '서연**', amount: 200000, biddedAt: Date.now() - 2 * 60 * 1000 },
      { id: 'b3', bidder: '나', amount: 210000, biddedAt: Date.now() - 1 * 60 * 1000, isMe: true },
    ],
  },
  {
    auctionId: 2,
    title: '애플워치 울트라2 (미개봉)',
    description: '미개봉 새 상품입니다.\n사이즈 49mm, 티타늄 케이스.',
    category: 'HOUSEHOLD',
    status: 'BID_ONGOING',
    images: [],
    startPrice: 480000,
    deposit: 40000,
    seller: { name: '급처직거래2', verified: true, dealCount: 42, rating: 4.7 },
    viewCount: 132,
    deliveryNote: '택배 거래만 가능합니다.',
    auctionType: 'DOWN',
    minimumPrice: 400000,
    dropPrice: 8000,
    priceDropIntervalMs: DOWN_DROP_INTERVAL_MS,
    startedAt: getOrInitStartedAt(2),
  },
  {
    auctionId: 3,
    title: '캠핑 4인용 텐트 세트',
    description: '2~3회 사용, 상태 좋습니다. 수납가방 포함.',
    category: 'HOUSEHOLD',
    status: 'BID_ONGOING',
    images: [],
    startPrice: 95000,
    deposit: 15000,
    seller: { name: '캠핑용품매니아', verified: true, dealCount: 21, rating: 4.6 },
    viewCount: 76,
    deliveryNote: '직거래만 가능합니다.',
    auctionType: 'DOWN',
    minimumPrice: 60000,
    dropPrice: 3000,
    priceDropIntervalMs: DOWN_DROP_INTERVAL_MS,
    startedAt: getOrInitStartedAt(3),
  },
  {
    auctionId: 4,
    title: '닌텐도 스위치 OLED',
    description: '사용감 있는 상품입니다. 박스/구성품 포함.',
    category: 'HOUSEHOLD',
    status: 'BID_ONGOING',
    images: [],
    startPrice: 220000,
    deposit: 20000,
    seller: { name: '중고나라짱', verified: false, dealCount: 8, rating: 4.2 },
    viewCount: 98,
    deliveryNote: '택배 거래만 가능합니다.',
    auctionType: 'UP',
    buyNowPrice: 300000,
    currentPrice: 260000,
    deadline: Date.now() + 20 * 60 * 1000,
    bidLog: [
      { id: 'b1', bidder: '하윤**', amount: 240000, biddedAt: Date.now() - 10 * 60 * 1000 },
      { id: 'b2', bidder: '도윤**', amount: 260000, biddedAt: Date.now() - 4 * 60 * 1000 },
    ],
  },
  {
    auctionId: 5,
    title: '르크루제 무쇠 냄비 세트',
    description: '혼수 선물로 받았으나 미사용. 정품입니다.',
    category: 'HOUSEHOLD',
    status: 'BID_ONGOING',
    images: [],
    startPrice: 145000,
    deposit: 20000,
    seller: { name: '주방용품셀러', verified: true, dealCount: 64, rating: 4.8 },
    viewCount: 143,
    deliveryNote: '택배 거래만 가능합니다.',
    auctionType: 'DOWN',
    minimumPrice: 100000,
    dropPrice: 4000,
    priceDropIntervalMs: DOWN_DROP_INTERVAL_MS,
    startedAt: getOrInitStartedAt(5),
  },
  {
    auctionId: 6,
    title: '다이슨 에어랩 컴플리트',
    description: '박스 개봉만 했고 사용은 안 했습니다.',
    category: 'HOUSEHOLD',
    status: 'BID_ONGOING',
    images: [],
    startPrice: 320000,
    deposit: 30000,
    seller: { name: '뷰티가전셀러', verified: true, dealCount: 37, rating: 4.7 },
    viewCount: 201,
    deliveryNote: '직거래 또는 택배 거래 가능합니다.',
    auctionType: 'DOWN',
    minimumPrice: 250000,
    dropPrice: 6000,
    priceDropIntervalMs: DOWN_DROP_INTERVAL_MS,
    startedAt: getOrInitStartedAt(6),
  },
  {
    auctionId: 7,
    title: '아이패드 프로 11 (M4)',
    description: '미개봉 새 제품입니다. 256GB, Wi-Fi 모델.',
    category: 'HOUSEHOLD',
    status: 'BID_ONGOING',
    images: [],
    startPrice: 800000,
    deposit: 60000,
    seller: { name: '애플정품셀러', verified: true, dealCount: 210, rating: 4.9 },
    viewCount: 302,
    deliveryNote: '택배 거래만 가능합니다.',
    auctionType: 'UP',
    buyNowPrice: 1050000,
    currentPrice: 890000,
    deadline: Date.now() + 15 * 60 * 1000,
    bidLog: [
      { id: 'b1', bidder: '지호**', amount: 850000, biddedAt: Date.now() - 6 * 60 * 1000 },
      { id: 'b2', bidder: '나', amount: 890000, biddedAt: Date.now() - 2 * 60 * 1000, isMe: true },
    ],
  },
  {
    auctionId: 8,
    title: '삼성 비스포크 냉장고',
    description: '이사로 인한 급처입니다. 사용감 적음.',
    category: 'HOUSEHOLD',
    status: 'BID_ONGOING',
    images: [],
    startPrice: 720000,
    deposit: 50000,
    seller: { name: '이사정리셀러', verified: false, dealCount: 5, rating: 4.3 },
    viewCount: 88,
    deliveryNote: '직거래만 가능합니다 (냉장고 특성상 배송이 어려워요).',
    auctionType: 'DOWN',
    minimumPrice: 600000,
    dropPrice: 10000,
    priceDropIntervalMs: DOWN_DROP_INTERVAL_MS,
    startedAt: getOrInitStartedAt(8),
  },
  {
    auctionId: 101,
    title: '[테스트] 종료된 경매 화면 확인용',
    description: '화면 상태 확인용 목업입니다.',
    category: 'HOUSEHOLD',
    status: 'COMPLETED',
    images: [],
    startPrice: 220000,
    deposit: 20000,
    seller: { name: '중고나라짱', verified: false, dealCount: 8, rating: 4.2 },
    viewCount: 98,
    deliveryNote: '택배 거래만 가능합니다.',
    auctionType: 'UP',
    buyNowPrice: 300000,
    currentPrice: 265000,
    deadline: Date.now() - 60 * 60 * 1000,
    bidLog: [
      { id: 'b1', bidder: '하윤**', amount: 250000, biddedAt: Date.now() - 2 * 60 * 60 * 1000 },
      { id: 'b2', bidder: '도윤**', amount: 265000, biddedAt: Date.now() - 90 * 60 * 1000 },
    ],
  },
  {
    auctionId: 102,
    title: '[테스트] 유찰된 하락경매 화면 확인용',
    description: '화면 상태 확인용 목업입니다.',
    category: 'HOUSEHOLD',
    status: 'UNSOLD',
    images: [],
    startPrice: 80000,
    deposit: 10000,
    seller: { name: '급처직거래3', verified: false, dealCount: 3, rating: 4.0 },
    viewCount: 12,
    deliveryNote: '직거래만 가능합니다.',
    auctionType: 'DOWN',
    minimumPrice: 50000,
    dropPrice: 5000,
    priceDropIntervalMs: DOWN_DROP_INTERVAL_MS,
    startedAt: Date.now() - 5 * 60 * 1000,
  },
]

function AuctionDetailPage() {
  const { auctionId } = useParams<{ auctionId: string }>()
  const found = MOCK_AUCTIONS.find((item) => item.auctionId === Number(auctionId))

  if (!found) return <NotFoundState />

  // 이전에 이 경매에 입찰/구매한 적이 있으면(같은 세션 안에서) 그 가격으로 시작한다.
  const livePrice = getLiveAuctionPrice(found.auctionId)
  const auction = found.auctionType === 'UP' && livePrice !== undefined
    ? { ...found, currentPrice: livePrice }
    : found

  return <AuctionDetailView key={auction.auctionId} auction={auction} />
}

function AuctionDetailView({ auction: initialAuction }: { auction: AuctionDetail }) {
  const [auction, setAuction] = useState(initialAuction)
  const { showToast } = useToast()

  function handleBidPlaced(amount: number) {
    if (auction.auctionType !== 'UP') return
    const newBid: BidEntry = {
      id: `local-${auction.bidLog.length}-${amount}`,
      bidder: '나',
      amount,
      biddedAt: Date.now(),
      isMe: true,
    }
    setAuction({ ...auction, currentPrice: amount, bidLog: [newBid, ...auction.bidLog] })
    setLiveAuctionPrice(auction.auctionId, amount)
    showToast(`${formatWon(amount)}으로 입찰했어요.`, 'success')
  }

  function handleBuyNow() {
    if (auction.auctionType !== 'UP') return
    const newBid: BidEntry = {
      id: `local-buynow-${auction.buyNowPrice}`,
      bidder: '나',
      amount: auction.buyNowPrice,
      biddedAt: Date.now(),
      isMe: true,
    }
    setAuction({
      ...auction,
      status: 'COMPLETED',
      currentPrice: auction.buyNowPrice,
      bidLog: [newBid, ...auction.bidLog],
    })
    setLiveAuctionPrice(auction.auctionId, auction.buyNowPrice)
    showToast(`${formatWon(auction.buyNowPrice)}에 즉시구매했어요.`, 'success')
  }

  return (
    <main className="mx-auto max-w-[1200px] px-lg py-xl max-[860px]:pb-32">
      <AuctionHeader auction={auction} />

      <div className="mt-lg grid grid-cols-1 gap-xl lg:grid-cols-[1fr_380px]">
        <div className="flex flex-col gap-xl">
          <AuctionGallery images={auction.images} title={auction.title} />
          <ProductTabs auction={auction} />
          {auction.auctionType === 'UP' ? (
            <BidHistoryPanel bidLog={auction.bidLog} />
          ) : (
            <PriceDropTimeline auction={auction} />
          )}
        </div>

        {/* top-[88px] = TopNav 높이(64px, sticky) + 여백(24px) — 안 맞추면 상단 네비랑 겹쳐서 밀려 보임 */}
        <div className="lg:sticky lg:top-[88px] lg:max-h-[calc(100dvh-104px)] lg:self-start lg:overflow-y-auto">
          {auction.auctionType === 'UP' ? (
            <UpBidPanel
              auction={auction}
              onBidPlaced={handleBidPlaced}
              onBuyNow={handleBuyNow}
            />
          ) : (
            <DownBuyPanel auction={auction} />
          )}
        </div>
      </div>

      <MobileStickyBar auction={auction} />
    </main>
  )
}

function NotFoundState() {
  const navigate = useNavigate()

  return (
    <main className="mx-auto flex max-w-[1200px] flex-col items-center gap-base px-lg py-section text-center">
      <p className="text-lg font-semibold text-ink">경매를 찾을 수 없어요.</p>
      <p className="text-sm text-body">삭제되었거나 잘못된 주소예요.</p>
      <Button variant="secondary" size="md" onClick={() => navigate('/auctions')}>
        경매 목록으로 돌아가기
      </Button>
    </main>
  )
}

function EmptyState({ message }: { message: string }) {
  return <p className="py-xl text-center text-sm text-muted">{message}</p>
}

function AuctionHeader({ auction }: { auction: AuctionDetail }) {
  const [interested, setInterested] = useState(false)

  return (
    <div className="flex flex-col gap-sm">
      <span className="text-xs text-muted">{CATEGORY_LABEL[auction.category]}</span>

      <div className="flex flex-wrap items-start justify-between gap-sm">
        <div>
          <div className="flex items-center gap-xs">
            <Badge tone={STATUS_BADGE_TONE[auction.status]}>{STATUS_LABEL[auction.status]}</Badge>
            <span className="text-xs text-muted">
              조회 {auction.viewCount.toLocaleString('ko-KR')}회
            </span>
          </div>
          <h1 className="mt-xs text-2xl font-bold text-ink">{auction.title}</h1>
        </div>

        <div className="flex shrink-0 gap-xs">
          <button
            type="button"
            onClick={() => setInterested((prev) => !prev)}
            className={`flex h-9 items-center gap-1 rounded-pill border px-base text-xs font-semibold transition-colors ${
              interested
                ? 'border-down-tint bg-down-tint text-down'
                : 'border-hairline bg-canvas text-body hover:bg-surface-strong'
            }`}
          >
            <Heart size={14} fill={interested ? 'currentColor' : 'none'} />
            관심
          </button>
          <button
            type="button"
            className="flex h-9 items-center gap-1 rounded-pill border border-hairline bg-canvas px-base text-xs font-semibold text-body hover:bg-surface-strong"
          >
            <Flag size={14} />
            신고
          </button>
        </div>
      </div>
    </div>
  )
}

function AuctionGallery({ images, title }: { images: string[]; title: string }) {
  const [active, setActive] = useState(0)
  const [broken, setBroken] = useState<Record<number, boolean>>({})

  const hasImages = images.length > 0
  const loadFailed = hasImages && broken[active]

  return (
    <div className="flex flex-col gap-sm">
      <div className="flex aspect-square items-center justify-center overflow-hidden rounded-xl bg-surface-soft">
        {!hasImages ? null : loadFailed ? (
          <div className="flex flex-col items-center gap-xs text-muted">
            <ImageOff size={32} />
            <span className="text-xs">이미지를 불러오지 못했어요</span>
          </div>
        ) : (
          <img
            src={images[active]}
            alt={title}
            className="h-full w-full object-cover"
            onError={() => setBroken((prev) => ({ ...prev, [active]: true }))}
          />
        )}
      </div>

      {hasImages && (
        <div className="flex gap-sm overflow-x-auto">
          {images.map((src, index) => (
            <button
              key={src}
              type="button"
              onClick={() => setActive(index)}
              aria-label={`상품 이미지 ${index + 1}`}
              className={`flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-md border-2 bg-surface-soft ${
                active === index ? 'border-ink' : 'border-transparent'
              }`}
            >
              {broken[index] ? (
                <ImageOff size={16} className="text-muted" />
              ) : (
                <img
                  src={src}
                  alt=""
                  className="h-full w-full object-cover"
                  onError={() => setBroken((prev) => ({ ...prev, [index]: true }))}
                />
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

const PRODUCT_TABS = ['상품 정보', '배송·거래', '판매자 정보'] as const
type ProductTabLabel = (typeof PRODUCT_TABS)[number]

function ProductTabs({ auction }: { auction: AuctionDetail }) {
  const [active, setActive] = useState<ProductTabLabel>(PRODUCT_TABS[0])

  return (
    <Card className="p-lg">
      <div role="tablist" className="flex gap-xs border-b border-hairline-soft pb-sm">
        {PRODUCT_TABS.map((tab) => (
          <button
            key={tab}
            type="button"
            role="tab"
            aria-selected={active === tab}
            onClick={() => setActive(tab)}
            className={`rounded-pill px-base py-1.5 text-sm font-semibold transition-colors ${
              active === tab ? 'bg-ink text-on-dark' : 'text-body hover:bg-surface-soft'
            }`}
          >
            {tab}
          </button>
        ))}
      </div>

      <div className="pt-base text-sm text-body">
        {active === '상품 정보' && <p className="whitespace-pre-line">{auction.description}</p>}
        {active === '배송·거래' && (
          <div className="flex items-start gap-sm">
            <Truck size={16} className="mt-0.5 shrink-0 text-muted" />
            <p>{auction.deliveryNote}</p>
          </div>
        )}
        {active === '판매자 정보' && <SellerInfo seller={auction.seller} />}
      </div>
    </Card>
  )
}

function SellerInfo({ seller }: { seller: Seller }) {
  return (
    <div className="flex items-center gap-sm">
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-surface-strong text-base font-bold text-body">
        {seller.name.slice(0, 1)}
      </span>
      <div>
        <div className="flex items-center gap-1 font-semibold text-ink">
          {seller.name}
          {seller.verified && <BadgeCheck size={14} className="text-primary" />}
        </div>
        <p className="text-xs text-muted">
          거래 {seller.dealCount}회 · 평점 {seller.rating.toFixed(1)}
        </p>
      </div>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted">{label}</p>
      <p className="font-semibold text-ink">{value}</p>
    </div>
  )
}

interface UpBidPanelProps {
  auction: UpAuctionDetail
  onBidPlaced: (amount: number) => void
  onBuyNow: () => void
}

function UpBidPanel({ auction, onBidPlaced, onBuyNow }: UpBidPanelProps) {
  const { remaining, isUrgent, isEnded } = useCountdown(auction.deadline)
  const nextMinBid = auction.currentPrice + BID_INCREMENT_OPTIONS[0]
  const [amount, setAmount] = useState(nextMinBid)
  const [error, setError] = useState<string | null>(null)

  const ended = isEnded || auction.status !== 'BID_ONGOING'

  function handleChip(increment: number) {
    setAmount(auction.currentPrice + increment)
    setError(null)
  }

  function handleAmountChange(event: ChangeEvent<HTMLInputElement>) {
    const raw = event.target.value.replace(/[^0-9]/g, '')
    setAmount(raw === '' ? 0 : Number(raw))
    setError(null)
  }

  function handleSubmit() {
    if (amount < nextMinBid) {
      setError(`최소 ${formatWon(nextMinBid)} 이상 입찰해주세요.`)
      return
    }
    onBidPlaced(amount)
    setAmount(amount + BID_INCREMENT_OPTIONS[0])
  }

  return (
    <Card className="flex flex-col gap-lg p-lg">
      <div className="flex items-center gap-xs">
        <Badge tone={ended ? 'ended' : 'live'}>{ended ? '마감' : '진행 중'}</Badge>
        <Badge tone="muted">판매자 인증</Badge>
      </div>

      <div>
        <p className="text-xs text-muted">현재 최고가</p>
        <p className="text-3xl font-bold text-ink">{formatWon(auction.currentPrice)}</p>
        <p
          className={`mt-1 flex items-center gap-1 text-sm font-semibold ${
            isUrgent ? 'text-down' : 'text-body'
          }`}
        >
          <Clock size={14} />
          {ended ? '경매 종료' : `${formatClock(remaining)} 남음`}
        </p>
      </div>

      <div className="grid grid-cols-2 gap-sm border-y border-hairline-soft py-base text-sm">
        <Stat label="입찰 수" value={`${auction.bidLog.length}회`} />
        <Stat label="조회" value={`${auction.viewCount.toLocaleString('ko-KR')}회`} />
        <Stat label="보증금" value={formatWon(auction.deposit)} />
        <Stat label="즉시구매가" value={formatWon(auction.buyNowPrice)} />
      </div>

      {!ended && (
        <>
          <div className="flex gap-sm">
            {BID_INCREMENT_OPTIONS.map((increment) => (
              <button
                key={increment}
                type="button"
                onClick={() => handleChip(increment)}
                className="flex-1 rounded-pill border border-hairline py-sm text-sm font-semibold text-body hover:bg-surface-strong"
              >
                +{increment.toLocaleString('ko-KR')}
              </button>
            ))}
          </div>

          <TextInput
            label="입찰 금액"
            inputMode="numeric"
            value={amount.toLocaleString('ko-KR')}
            onChange={handleAmountChange}
            error={error ?? undefined}
          />

          <Button variant="primary" size="lg" onClick={handleSubmit}>
            {formatWon(amount)}으로 입찰하기
          </Button>
          <Button variant="secondary" size="md" onClick={onBuyNow}>
            즉시구매 {formatWon(auction.buyNowPrice)}
          </Button>
        </>
      )}
    </Card>
  )
}

function DownBuyPanel({ auction }: { auction: DownAuctionDetail }) {
  const { showToast } = useToast()
  const { currentPrice, remaining, isUrgent, atFloor } = useDownAuctionClock(auction)
  const ended = auction.status !== 'BID_ONGOING'

  function handleBuy() {
    showToast(`${formatWon(currentPrice)}에 구매를 확정했어요.`, 'success')
  }

  return (
    <Card className="flex flex-col gap-lg p-lg">
      <div className="flex items-center gap-xs">
        <Badge tone={ended ? 'ended' : 'live'}>{ended ? '마감' : '하락 중'}</Badge>
        <Badge tone="muted">판매자 인증</Badge>
      </div>

      <div>
        <p className="text-xs text-muted">지금 이 가격</p>
        <p className="flex items-center gap-1.5">
          <span
            key={currentPrice}
            className="text-3xl font-bold text-down"
            style={{ animation: 'price-drop-pop 0.5s ease-out' }}
          >
            {formatWon(currentPrice)}
          </span>
          {!ended && !atFloor && (
            <TrendingDown size={20} className="animate-bounce text-down" />
          )}
        </p>
        {!ended && !atFloor && (
          <p
            className={`mt-1 flex items-center gap-1 text-sm font-semibold ${
              isUrgent ? 'text-down' : 'text-body'
            }`}
          >
            <Clock size={14} />
            {formatClock(remaining)} 후 추가 하락
          </p>
        )}
        {!ended && atFloor && (
          <p className="mt-1 text-sm font-semibold text-body">최저가에 도달했어요</p>
        )}
      </div>

      <DropProgress
        startPrice={auction.startPrice}
        minimumPrice={auction.minimumPrice}
        currentPrice={currentPrice}
      />

      <div className="grid grid-cols-2 gap-sm border-y border-hairline-soft py-base text-sm">
        <Stat label="시작가" value={formatWon(auction.startPrice)} />
        <Stat label="최저가" value={formatWon(auction.minimumPrice)} />
        <Stat label="조회" value={`${auction.viewCount.toLocaleString('ko-KR')}회`} />
        <Stat label="보증금" value={formatWon(auction.deposit)} />
      </div>

      {!ended && (
        <>
          <Button variant="primary" size="lg" onClick={handleBuy}>
            {formatWon(currentPrice)}에 구매하기
          </Button>
          <p className="text-center text-xs text-muted">
            시간이 지날수록 가격이 자동으로 내려가요. 선착순으로 구매가 확정됩니다.
          </p>
        </>
      )}
    </Card>
  )
}

function DropProgress({
  startPrice,
  minimumPrice,
  currentPrice,
}: {
  startPrice: number
  minimumPrice: number
  currentPrice: number
}) {
  const totalRange = startPrice - minimumPrice
  const dropped = startPrice - currentPrice
  const percent = totalRange > 0 ? Math.min(100, Math.max(0, (dropped / totalRange) * 100)) : 0

  if (dropped <= 0) return null

  return (
    <div>
      <div className="h-2 w-full overflow-hidden rounded-full bg-surface-strong">
        <div
          className="h-full rounded-full bg-down transition-[width] duration-500 ease-out"
          style={{ width: `${percent}%` }}
        />
      </div>
      <p className="mt-1.5 flex items-center gap-1 text-xs font-semibold text-down">
        <TrendingDown size={12} />
        시작가 대비 {formatWon(dropped)} 하락
      </p>
    </div>
  )
}

function BidHistoryPanel({ bidLog }: { bidLog: BidEntry[] }) {
  return (
    <Card className="p-lg">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-bold text-ink">입찰 기록</h2>
        <span className="text-sm text-muted">전체 {bidLog.length}회</span>
      </div>

      {bidLog.length === 0 ? (
        <EmptyState message="아직 입찰 기록이 없어요." />
      ) : (
        <ul
          aria-live="polite"
          className="mt-base flex max-h-[360px] flex-col divide-y divide-hairline-soft overflow-y-auto"
        >
          {bidLog.map((bid) => (
            <li key={bid.id} className="flex items-center justify-between py-sm text-sm">
              <span className="text-muted">{formatTimeOfDay(new Date(bid.biddedAt))}</span>
              <span className={bid.isMe ? 'font-semibold text-primary' : 'text-body'}>
                {bid.isMe ? '나의 입찰' : bid.bidder}
              </span>
              <span className={`font-semibold ${bid.isMe ? 'text-primary' : 'text-ink'}`}>
                {formatWon(bid.amount)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function PriceDropTimeline({ auction }: { auction: DownAuctionDetail }) {
  // 1초마다 재렌더링돼야 새로 떨어진 가격이 목록에 바로 반영된다.
  useDownAuctionClock(auction)
  const history = computeDropHistory(auction, Date.now())
  const newestFirst = [...history].reverse()

  return (
    <Card className="p-lg">
      <h2 className="text-lg font-bold text-ink">전체 가격 변동 내역</h2>

      {newestFirst.length === 0 ? (
        <EmptyState message="아직 가격이 내려간 적이 없어요." />
      ) : (
        <ul className="mt-base flex flex-col divide-y divide-hairline-soft">
          {newestFirst.map((entry, index) => (
            <li
              key={entry.droppedAt}
              style={index === 0 ? { animation: 'drop-row-flash 1.4s ease-out' } : undefined}
              className="flex items-center justify-between rounded-md px-xs py-sm text-sm"
            >
              <span className="text-muted">{formatTimeOfDay(new Date(entry.droppedAt))}</span>
              <span className="flex items-center gap-1 font-semibold text-down">
                <TrendingDown size={14} />
                {formatWon(entry.price)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function MobileStickyBar({ auction }: { auction: AuctionDetail }) {
  return auction.auctionType === 'UP' ? (
    <UpMobileStickyBar auction={auction} />
  ) : (
    <DownMobileStickyBar auction={auction} />
  )
}

function UpMobileStickyBar({ auction }: { auction: UpAuctionDetail }) {
  const { showToast } = useToast()
  const { remaining, isUrgent, isEnded } = useCountdown(auction.deadline)
  const ended = isEnded || auction.status !== 'BID_ONGOING'

  function handleQuickAction() {
    showToast(`${formatWon(auction.currentPrice + BID_INCREMENT_OPTIONS[0])}으로 입찰했어요.`, 'success')
  }

  return (
    <StickyBarView
      price={auction.currentPrice}
      remaining={remaining}
      isUrgent={isUrgent}
      ended={ended}
      actionLabel="빠른 입찰"
      onAction={handleQuickAction}
    />
  )
}

function DownMobileStickyBar({ auction }: { auction: DownAuctionDetail }) {
  const { showToast } = useToast()
  const { currentPrice, remaining, isUrgent } = useDownAuctionClock(auction)
  const ended = auction.status !== 'BID_ONGOING'

  function handleQuickAction() {
    showToast(`${formatWon(currentPrice)}에 구매를 확정했어요.`, 'success')
  }

  return (
    <StickyBarView
      price={currentPrice}
      remaining={remaining}
      isUrgent={isUrgent}
      ended={ended}
      actionLabel="구매하기"
      onAction={handleQuickAction}
    />
  )
}

interface StickyBarViewProps {
  price: number
  remaining: number
  isUrgent: boolean
  ended: boolean
  actionLabel: string
  onAction: () => void
}

function StickyBarView({ price, remaining, isUrgent, ended, actionLabel, onAction }: StickyBarViewProps) {
  return (
    <div className="fixed inset-x-0 bottom-0 z-40 hidden items-center justify-between gap-base border-t border-hairline bg-canvas px-lg py-sm shadow-card max-[860px]:flex">
      <div>
        <p className="text-xs text-muted">현재가</p>
        <p className="text-lg font-bold text-ink">{formatWon(price)}</p>
        {!ended && (
          <p className={`text-xs font-semibold ${isUrgent ? 'text-down' : 'text-muted'}`}>
            {formatClock(remaining)} 남음
          </p>
        )}
      </div>
      <Button variant="primary" size="md" disabled={ended} onClick={onAction}>
        {ended ? '경매 종료' : actionLabel}
      </Button>
    </div>
  )
}

export default AuctionDetailPage
