import { Clock, TrendingDown, TrendingUp } from 'lucide-react'
import { Link } from 'react-router-dom'
import Card from '../components/ui/Card'
import { useCountdown } from '../hooks/useCountdown'
import { formatClock, formatWon } from '../lib/format'

/*
 * "인기순 5개"를 내려주는 API가 아직 없어서 임시 데이터를 쓴다.
 * 실제 연동 시 이 배열을 fetch 결과로 바꾸면 된다 (예: src/routes/api.ts).
 */
const POPULAR_AUCTION_LIMIT = 5
const UP_AUCTION_LABEL = '상향 경매'
const DOWN_AUCTION_LABEL = '하락 중'

type AuctionType = 'UP' | 'DOWN'

interface PopularAuction {
  auctionId: number
  title: string
  auctionType: AuctionType
  currentPrice: number
  deadline: number
}

const MOCK_POPULAR_AUCTIONS: PopularAuction[] = [
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
    currentPrice: 480000,
    deadline: Date.now() + 3 * 60 * 1000,
  },
  {
    auctionId: 3,
    title: '캠핑 4인용 텐트 세트',
    auctionType: 'DOWN',
    currentPrice: 95000,
    deadline: Date.now() + 12 * 60 * 1000,
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
    currentPrice: 145000,
    deadline: Date.now() + 5 * 60 * 1000,
  },
]

function HomePage() {
  return (
    <main className="mx-auto max-w-[1200px] px-lg py-xl">
      <h1 className="text-2xl font-bold text-ink">지금 인기 있는 경매 TOP 5</h1>
      <p className="mt-xs text-sm text-body">
        조회·입찰이 가장 활발한 경매예요. 하락 중인 경매는 시간이 지날수록 가격이
        떨어지니 서두르세요.
      </p>

      <div className="mt-lg grid grid-cols-1 gap-lg sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
        {MOCK_POPULAR_AUCTIONS.slice(0, POPULAR_AUCTION_LIMIT).map((auction) => (
          <AuctionSummaryCard key={auction.auctionId} auction={auction} />
        ))}
      </div>
    </main>
  )
}

function AuctionSummaryCard({ auction }: { auction: PopularAuction }) {
  const { remaining, isUrgent } = useCountdown(auction.deadline)
  const isDown = auction.auctionType === 'DOWN'

  return (
    <Link to={`/auctions/${auction.auctionId}`} className="block">
      <Card className="flex h-full flex-col gap-sm hover:shadow-soft">
        <div className="aspect-square rounded-md bg-surface-soft" />

        <span
          className={`inline-flex w-fit items-center gap-1 rounded-pill px-sm py-0.5 text-xs font-semibold ${
            isDown ? 'bg-down-tint text-down' : 'bg-surface-strong text-body'
          }`}
        >
          {isDown ? <TrendingDown size={12} /> : <TrendingUp size={12} />}
          {isDown ? DOWN_AUCTION_LABEL : UP_AUCTION_LABEL}
        </span>

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

export default HomePage
