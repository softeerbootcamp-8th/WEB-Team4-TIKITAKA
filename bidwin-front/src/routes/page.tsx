import { Clock, Flame, TrendingDown, TrendingUp } from 'lucide-react'
import { Link } from 'react-router-dom'
import Badge from '../components/ui/Badge'
import Card from '../components/ui/Card'
import { useCountdown } from '../hooks/useCountdown'
import { formatClock, formatWon } from '../lib/format'

/*
 * 경매 목록 API가 아직 없어서 임시 데이터를 쓴다.
 * 실제 연동 시 이 배열을 fetch 결과로 바꾸면 된다 (예: src/routes/api.ts).
 */
const POPULAR_AUCTION_LIMIT = 5
const SPLIT_LIST_LIMIT = 4
const UP_AUCTION_LABEL = '상향 경매'
const DOWN_AUCTION_LABEL = '하락 중'

type AuctionType = 'UP' | 'DOWN'

interface AuctionSummary {
  auctionId: number
  title: string
  auctionType: AuctionType
  currentPrice: number
  deadline: number
}

const MOCK_AUCTIONS: AuctionSummary[] = [
  { auctionId: 1, title: '소니 WH-1000XM5 노이즈캔슬링 헤드폰 (미개봉)', auctionType: 'UP', currentPrice: 210000, deadline: Date.now() + 8 * 60 * 1000 },
  { auctionId: 2, title: '애플워치 울트라2 (미개봉)', auctionType: 'DOWN', currentPrice: 480000, deadline: Date.now() + 3 * 60 * 1000 },
  { auctionId: 3, title: '캠핑 4인용 텐트 세트', auctionType: 'DOWN', currentPrice: 95000, deadline: Date.now() + 12 * 60 * 1000 },
  { auctionId: 4, title: '닌텐도 스위치 OLED', auctionType: 'UP', currentPrice: 260000, deadline: Date.now() + 20 * 60 * 1000 },
  { auctionId: 5, title: '르크루제 무쇠 냄비 세트', auctionType: 'DOWN', currentPrice: 145000, deadline: Date.now() + 5 * 60 * 1000 },
  { auctionId: 6, title: '다이슨 에어랩 컴플리트', auctionType: 'DOWN', currentPrice: 320000, deadline: Date.now() + 90 * 1000 },
  { auctionId: 7, title: '아이패드 프로 11 (M4)', auctionType: 'UP', currentPrice: 890000, deadline: Date.now() + 15 * 60 * 1000 },
  { auctionId: 8, title: '삼성 비스포크 냉장고', auctionType: 'DOWN', currentPrice: 720000, deadline: Date.now() + 6 * 60 * 1000 },
]

function HomePage() {
  const popularAuctions = MOCK_AUCTIONS.slice(0, POPULAR_AUCTION_LIMIT)
  const upAuctions = MOCK_AUCTIONS.filter((a) => a.auctionType === 'UP').slice(0, SPLIT_LIST_LIMIT)
  const downAuctions = MOCK_AUCTIONS.filter((a) => a.auctionType === 'DOWN').slice(
    0,
    SPLIT_LIST_LIMIT,
  )
  const spotlight =
    downAuctions.length > 0
      ? downAuctions.reduce((earliest, auction) =>
          auction.deadline < earliest.deadline ? auction : earliest,
        )
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

function SpotlightBanner({ auction }: { auction: AuctionSummary }) {
  const { remaining, isUrgent } = useCountdown(auction.deadline)

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
            <div className="text-2xl font-bold text-down">{formatWon(auction.currentPrice)}</div>
            <div
              className={`flex items-center justify-end gap-1 text-sm font-semibold ${
                isUrgent ? 'text-down' : 'text-body'
              }`}
            >
              <Clock size={14} />
              {formatClock(remaining)} 남음
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
  const { remaining, isUrgent } = useCountdown(auction.deadline)
  const isDown = auction.auctionType === 'DOWN'

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
          <span className={`text-lg font-bold ${isDown ? 'text-down' : 'text-ink'}`}>
            {formatWon(auction.currentPrice)}
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

function AuctionListRow({
  auction,
  accent,
}: {
  auction: AuctionSummary
  accent?: boolean
}) {
  const { remaining, isUrgent } = useCountdown(auction.deadline)

  return (
    <li>
      <Link
        to={`/auctions/${auction.auctionId}`}
        className="flex items-center gap-sm py-sm first:pt-0 last:pb-0"
      >
        <div className="h-11 w-11 shrink-0 rounded-md bg-surface-soft" />
        <span className="line-clamp-1 flex-1 text-sm font-medium text-ink">{auction.title}</span>
        <span className={`shrink-0 text-sm font-bold ${accent ? 'text-down' : 'text-ink'}`}>
          {formatWon(auction.currentPrice)}
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
