import { Clock, Flame, TrendingDown, TrendingUp } from 'lucide-react'
import { Link } from 'react-router-dom'
import Badge from '../components/ui/Badge'
import Card from '../components/ui/Card'
import { useCountdown } from '../hooks/useCountdown'
import { useDownAuctionClock } from '../hooks/useDownAuctionClock'
import { getLiveAuctionPrice, getOrInitStartedAt } from '../lib/auctionLiveStore'
import { computeCurrentDownPrice } from '../lib/auctionPricing'
import { formatClock, formatWon } from '../lib/format'

/*
 * 경매 목록 API가 아직 없어서 임시 데이터를 쓴다.
 * 실제 연동 시 이 배열을 fetch 결과로 바꾸면 된다 (예: src/routes/api.ts).
 * 하락경매(DOWN) 항목은 경매 상세 페이지(src/routes/auctions/detail/page.tsx)의
 * 같은 auctionId와 시작가/최저가/하락폭/하락주기를 반드시 맞춰야 두 화면의 가격이
 * 어긋나지 않는다.
 */
const POPULAR_AUCTION_LIMIT = 5
const SPLIT_LIST_LIMIT = 4
const UP_AUCTION_LABEL = '상향 경매'
const DOWN_AUCTION_LABEL = '하락 중'
const DOWN_DROP_INTERVAL_MS = 15 * 1000

interface AuctionSummaryBase {
  auctionId: number
  title: string
}

interface UpAuctionSummary extends AuctionSummaryBase {
  auctionType: 'UP'
  currentPrice: number
  deadline: number
}

interface DownAuctionSummary extends AuctionSummaryBase {
  auctionType: 'DOWN'
  startPrice: number
  minimumPrice: number
  dropPrice: number
  priceDropIntervalMs: number
  startedAt: number
}

type AuctionSummary = UpAuctionSummary | DownAuctionSummary

const MOCK_AUCTIONS: AuctionSummary[] = [
  {
    auctionId: 1,
    title: '소니 WH-1000XM5 노이즈캔슬링 헤드폰 (미개봉)',
    auctionType: 'UP',
    currentPrice: 210000,
    deadline: Date.now() + 8 * 60 * 1000,
  },
  {
    auctionId: 2,
    title: '애플워치 울트라2 (미개봉)',
    auctionType: 'DOWN',
    startPrice: 480000,
    minimumPrice: 400000,
    dropPrice: 8000,
    priceDropIntervalMs: DOWN_DROP_INTERVAL_MS,
    startedAt: getOrInitStartedAt(2),
  },
  {
    auctionId: 3,
    title: '캠핑 4인용 텐트 세트',
    auctionType: 'DOWN',
    startPrice: 95000,
    minimumPrice: 60000,
    dropPrice: 3000,
    priceDropIntervalMs: DOWN_DROP_INTERVAL_MS,
    startedAt: getOrInitStartedAt(3),
  },
  {
    auctionId: 4,
    title: '닌텐도 스위치 OLED',
    auctionType: 'UP',
    currentPrice: 260000,
    deadline: Date.now() + 20 * 60 * 1000,
  },
  {
    auctionId: 5,
    title: '르크루제 무쇠 냄비 세트',
    auctionType: 'DOWN',
    startPrice: 145000,
    minimumPrice: 100000,
    dropPrice: 4000,
    priceDropIntervalMs: DOWN_DROP_INTERVAL_MS,
    startedAt: getOrInitStartedAt(5),
  },
  {
    auctionId: 6,
    title: '다이슨 에어랩 컴플리트',
    auctionType: 'DOWN',
    startPrice: 320000,
    minimumPrice: 250000,
    dropPrice: 6000,
    priceDropIntervalMs: DOWN_DROP_INTERVAL_MS,
    startedAt: getOrInitStartedAt(6),
  },
  {
    auctionId: 7,
    title: '아이패드 프로 11 (M4)',
    auctionType: 'UP',
    currentPrice: 890000,
    deadline: Date.now() + 15 * 60 * 1000,
  },
  {
    auctionId: 8,
    title: '삼성 비스포크 냉장고',
    auctionType: 'DOWN',
    startPrice: 720000,
    minimumPrice: 600000,
    dropPrice: 10000,
    priceDropIntervalMs: DOWN_DROP_INTERVAL_MS,
    startedAt: getOrInitStartedAt(8),
  },
]

function HomePage() {
  const popularAuctions = MOCK_AUCTIONS.slice(0, POPULAR_AUCTION_LIMIT)
  const upAuctions = MOCK_AUCTIONS.filter(
    (a): a is UpAuctionSummary => a.auctionType === 'UP',
  ).slice(0, SPLIT_LIST_LIMIT)
  const downAuctions = MOCK_AUCTIONS.filter(
    (a): a is DownAuctionSummary => a.auctionType === 'DOWN',
  ).slice(0, SPLIT_LIST_LIMIT)

  const now = Date.now()
  const spotlight =
    downAuctions.length > 0
      ? downAuctions.reduce((mostDropped, auction) => {
          const droppedRatio =
            (auction.startPrice - computeCurrentDownPrice(auction, now)) /
            (auction.startPrice - auction.minimumPrice || 1)
          const mostDroppedRatio =
            (mostDropped.startPrice - computeCurrentDownPrice(mostDropped, now)) /
            (mostDropped.startPrice - mostDropped.minimumPrice || 1)
          return droppedRatio > mostDroppedRatio ? auction : mostDropped
        })
      : null

  return (
    <main className="mx-auto max-w-[1200px] px-lg py-xl">
      {spotlight && <SpotlightBanner auction={spotlight} />}

      <section className="mt-xl">
        <h1 className="text-2xl font-bold text-ink">지금 인기 있는 경매 TOP 5</h1>
        <p className="mt-xs text-sm text-body">
          조회·입찰이 가장 활발한 경매예요. 하락 중인 경매는 시간이 지날수록 가격이
          떨어지니 서두르세요.
        </p>

        <div className="mt-lg grid grid-cols-1 gap-lg sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
          {popularAuctions.map((auction) => (
            <AuctionSummaryCard key={auction.auctionId} auction={auction} />
          ))}
        </div>
      </section>

      <section className="mt-section grid grid-cols-1 gap-lg lg:grid-cols-2">
        <AuctionListPanel
          title="상승 중인 경매"
          description="입찰이 들어올수록 가격이 오르는 일반 경매예요."
          auctions={upAuctions}
        />
        <AuctionListPanel
          title="가격이 빠르게 떨어지는 중"
          description="시간이 지날수록 가격이 내려가요. 원하는 가격일 때 바로 잡으세요."
          auctions={downAuctions}
          accent
        />
      </section>
    </main>
  )
}

function SpotlightBanner({ auction }: { auction: DownAuctionSummary }) {
  const { currentPrice, remaining, isUrgent } = useDownAuctionClock(auction)

  return (
    <Link to={`/auctions/${auction.auctionId}`} className="block">
      <div className="flex flex-col gap-md rounded-xl border border-down-tint bg-down-tint px-xl py-lg sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-sm">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-down text-on-primary">
            <Flame size={20} />
          </span>
          <div>
            <span className="inline-flex items-center gap-1 text-xs font-semibold text-down">
              <TrendingDown size={12} />
              지금 가장 빠르게 떨어지는 중
            </span>
            <h2 className="mt-0.5 text-lg font-bold text-ink">{auction.title}</h2>
          </div>
        </div>

        <div className="flex items-center gap-lg">
          <div className="text-right">
            <div
              key={currentPrice}
              className="text-2xl font-bold text-down"
              style={{ animation: 'price-drop-pop 0.5s ease-out' }}
            >
              {formatWon(currentPrice)}
            </div>
            <div
              className={`flex items-center justify-end gap-1 text-sm font-semibold ${
                isUrgent ? 'text-down' : 'text-body'
              }`}
            >
              <Clock size={14} />
              {formatClock(remaining)} 후 추가 하락
            </div>
          </div>
          <span className="inline-flex h-11 shrink-0 items-center justify-center rounded-pill bg-primary px-lg text-base font-semibold text-on-primary">
            지금 보기
          </span>
        </div>
      </div>
    </Link>
  )
}

function AuctionSummaryCard({ auction }: { auction: AuctionSummary }) {
  // useCountdown/useDownAuctionClock 둘 다 조건 없이 호출해야 해서(Hooks 규칙),
  // 타입별로 컴포넌트를 나누고 실제 렌더링만 아래 공용 뷰에서 같이 처리한다.
  return auction.auctionType === 'UP' ? (
    <UpSummaryCard auction={auction} />
  ) : (
    <DownSummaryCard auction={auction} />
  )
}

function UpSummaryCard({ auction }: { auction: UpAuctionSummary }) {
  const price = getLiveAuctionPrice(auction.auctionId) ?? auction.currentPrice
  const { remaining, isUrgent } = useCountdown(auction.deadline)
  return (
    <SummaryCardView
      auction={auction}
      isDown={false}
      price={price}
      remaining={remaining}
      isUrgent={isUrgent}
    />
  )
}

function DownSummaryCard({ auction }: { auction: DownAuctionSummary }) {
  const { currentPrice, remaining, isUrgent } = useDownAuctionClock(auction)
  return (
    <SummaryCardView
      auction={auction}
      isDown
      price={currentPrice}
      remaining={remaining}
      isUrgent={isUrgent}
    />
  )
}

interface SummaryCardViewProps {
  auction: AuctionSummary
  isDown: boolean
  price: number
  remaining: number
  isUrgent: boolean
}

function SummaryCardView({ auction, isDown, price, remaining, isUrgent }: SummaryCardViewProps) {
  return (
    <Link to={`/auctions/${auction.auctionId}`} className="block">
      <Card className="flex h-full flex-col gap-sm hover:shadow-soft">
        <div className="aspect-square rounded-md bg-surface-soft" />

        <Badge tone={isDown ? 'danger' : 'neutral'}>
          {isDown ? <TrendingDown size={12} /> : <TrendingUp size={12} />}
          {isDown ? DOWN_AUCTION_LABEL : UP_AUCTION_LABEL}
        </Badge>

        <h2 className="line-clamp-2 min-h-[2.5em] text-sm font-semibold text-ink">
          {auction.title}
        </h2>

        <div className="mt-auto flex items-end justify-between">
          <span
            key={isDown ? price : undefined}
            className={`text-lg font-bold ${isDown ? 'text-down' : 'text-ink'}`}
            style={isDown ? { animation: 'price-drop-pop 0.5s ease-out' } : undefined}
          >
            {formatWon(price)}
          </span>
          <span
            className={`flex items-center gap-1 text-xs ${
              isUrgent ? 'text-down' : 'text-muted'
            }`}
          >
            <Clock size={12} />
            {formatClock(remaining)}
          </span>
        </div>
      </Card>
    </Link>
  )
}

interface AuctionListPanelProps {
  title: string
  description: string
  auctions: AuctionSummary[]
  accent?: boolean
}

function AuctionListPanel({ title, description, auctions, accent }: AuctionListPanelProps) {
  return (
    <div className="rounded-xl border border-hairline-soft bg-canvas p-lg">
      <h2 className="text-lg font-bold text-ink">{title}</h2>
      <p className="mt-xs text-sm text-body">{description}</p>

      <ul className="mt-base flex flex-col divide-y divide-hairline-soft">
        {auctions.map((auction) => (
          <AuctionListRow key={auction.auctionId} auction={auction} accent={accent} />
        ))}
      </ul>
    </div>
  )
}

function AuctionListRow({ auction, accent }: { auction: AuctionSummary; accent?: boolean }) {
  return auction.auctionType === 'UP' ? (
    <UpListRow auction={auction} accent={accent} />
  ) : (
    <DownListRow auction={auction} accent={accent} />
  )
}

function UpListRow({ auction, accent }: { auction: UpAuctionSummary; accent?: boolean }) {
  const price = getLiveAuctionPrice(auction.auctionId) ?? auction.currentPrice
  const { remaining, isUrgent } = useCountdown(auction.deadline)
  return (
    <ListRowView
      auction={auction}
      accent={accent}
      price={price}
      remaining={remaining}
      isUrgent={isUrgent}
    />
  )
}

function DownListRow({ auction, accent }: { auction: DownAuctionSummary; accent?: boolean }) {
  const { currentPrice, remaining, isUrgent } = useDownAuctionClock(auction)
  return (
    <ListRowView
      auction={auction}
      accent={accent}
      price={currentPrice}
      remaining={remaining}
      isUrgent={isUrgent}
    />
  )
}

interface ListRowViewProps {
  auction: AuctionSummary
  accent?: boolean
  price: number
  remaining: number
  isUrgent: boolean
}

function ListRowView({ auction, accent, price, remaining, isUrgent }: ListRowViewProps) {
  return (
    <li>
      <Link
        to={`/auctions/${auction.auctionId}`}
        className="flex items-center gap-sm py-sm first:pt-0 last:pb-0"
      >
        <div className="h-11 w-11 shrink-0 rounded-md bg-surface-soft" />
        <span className="line-clamp-1 flex-1 text-sm font-medium text-ink">{auction.title}</span>
        <span
          key={accent ? price : undefined}
          className={`shrink-0 text-sm font-bold ${accent ? 'text-down' : 'text-ink'}`}
          style={accent ? { animation: 'price-drop-pop 0.5s ease-out' } : undefined}
        >
          {formatWon(price)}
        </span>
        <span
          className={`flex shrink-0 items-center gap-1 text-xs ${
            isUrgent ? 'text-down' : 'text-muted'
          }`}
        >
          <Clock size={11} />
          {formatClock(remaining)}
        </span>
      </Link>
    </li>
  )
}

export default HomePage
