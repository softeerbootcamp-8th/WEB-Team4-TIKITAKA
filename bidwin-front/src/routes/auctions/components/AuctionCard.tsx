import { Clock, Gavel, ImageIcon, TrendingDown, TrendingUp } from 'lucide-react'
import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import RollingPrice from '../../../components/ui/RollingPrice'
import { useCountdown } from '../../../hooks/useCountdown'
import { useDownAuctionClock } from '../../../hooks/useDownAuctionClock'
import { useRecentChange } from '../../../hooks/useRecentChange'
import { formatClock } from '../../../lib/format'
import { CLOSE_HIGHLIGHT_MS, closePopStyle, closeSweepStyle } from '../../../lib/motion'
import { CARD_TEXT } from '../constants'
import type { AuctionSummary } from '../types'

const TYPE_LABEL = { UP: '상향 경매', DOWN: '하향 경매' } as const
const CATEGORY_LABEL = {
  HOUSEHOLD: '생활용품',
  FOOD: '먹거리',
  FURNITURE: '가구',
} as const
const PERCENT_BASE = 100
const THUMBNAIL_CLASS = 'aspect-square w-28 shrink-0 overflow-hidden rounded-lg md:w-full'
const CARD_SURFACE_CLASS = 'rounded-xl border border-hairline-soft bg-canvas'

function isOngoing(auction: AuctionSummary) {
  return auction.status === 'OPEN' || auction.status === 'BID_ONGOING'
}

function AuctionTypeBadge({ auctionType, overlay }: {
  auctionType: AuctionSummary['auctionType']
  overlay?: boolean
}) {
  const isDown = auctionType === 'DOWN'
  const toneClass = isDown
    ? overlay ? 'bg-down-tint/95 text-down backdrop-blur-sm' : 'bg-down-tint text-down'
    : overlay ? 'bg-canvas/90 text-body backdrop-blur-sm' : 'bg-surface-strong text-body'

  return (
    <span className={`inline-flex h-6 shrink-0 items-center gap-1 whitespace-nowrap rounded-pill px-2 text-[11px] font-semibold ${toneClass}`}>
      {isDown ? <TrendingDown size={11} /> : <TrendingUp size={11} />}
      {TYPE_LABEL[auctionType]}
    </span>
  )
}

interface AuctionCardProps {
  auction: AuctionSummary
  serverOffsetMs: number
}

function AuctionCard(props: AuctionCardProps) {
  const { auction } = props
  if (auction.auctionType !== 'DOWN' || !auction.downPricing || !isOngoing(auction)) {
    return <AuctionCardView {...props} currentPrice={auction.currentPrice} />
  }
  return <TimedDownAuctionCard {...props} />
}

function TimedDownAuctionCard(props: AuctionCardProps) {
  const { auction, serverOffsetMs } = props
  // 부모가 하향 진행 경매이면서 pricing이 있을 때만 이 컴포넌트를 렌더링한다.
  const pricing = auction.downPricing!
  const clockPricing = useMemo(() => ({
    startPrice: auction.startPrice,
    minimumPrice: pricing.minimumPrice,
    dropPrice: pricing.dropPrice,
    priceDropIntervalMs: pricing.priceDropIntervalMs,
    startedAt: pricing.startedAt,
  }), [
    auction.startPrice,
    pricing.dropPrice,
    pricing.minimumPrice,
    pricing.priceDropIntervalMs,
    pricing.startedAt,
  ])
  const { currentPrice } = useDownAuctionClock(clockPricing, serverOffsetMs)
  return <AuctionCardView {...props} currentPrice={currentPrice} />
}

function AuctionCardView({
  auction,
  serverOffsetMs,
  currentPrice,
}: AuctionCardProps & { currentPrice: number }) {
  const localDeadline = useMemo(
    () => auction.deadline - serverOffsetMs,
    [auction.deadline, serverOffsetMs],
  )
  const countdown = useCountdown(localDeadline)
  const ended = !isOngoing(auction) || countdown.isEnded
  /* 목록에서도 어느 카드가 방금 마감됐는지 보이도록 카드 전체를 한 번 훑는다. */
  const justClosed = useRecentChange(ended, CLOSE_HIGHLIGHT_MS) && ended
  const isDown = auction.auctionType === 'DOWN'
  const dropRate = Math.max(0, Math.round(
    ((auction.startPrice - currentPrice) / auction.startPrice) * PERCENT_BASE,
  ))

  return (
    <article
      style={closeSweepStyle(justClosed)}
      className={`relative flex h-full gap-sm p-sm transition-shadow hover:shadow-card md:flex-col ${CARD_SURFACE_CLASS}`}
    >
      <Link
        to={`/auctions/${auction.auctionId}`}
        aria-label={auction.title}
        className="absolute inset-0 z-10 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      />

      <div className={`relative ${THUMBNAIL_CLASS}`}>
        {auction.thumbnailUrl ? (
          <img src={auction.thumbnailUrl} alt="" className="h-full w-full object-cover" />
        ) : (
          <span className="flex h-full w-full items-center justify-center bg-surface-soft text-muted-soft">
            <ImageIcon size={28} />
          </span>
        )}
        <div className="absolute left-2 top-2 hidden md:block">
          <AuctionTypeBadge auctionType={auction.auctionType} overlay />
        </div>
      </div>

      <div className="flex min-w-0 flex-1 flex-col gap-1 md:mt-1 md:flex-none">
        <div className="min-w-0">
          <p className="line-clamp-1 text-xs font-semibold text-ink">{auction.sellerName}</p>
          <p className="line-clamp-1 text-[11px] text-primary">
            {CATEGORY_LABEL[auction.category]}
          </p>
        </div>

        <h2 className="line-clamp-2 text-sm font-bold leading-snug text-ink">
          {auction.title}
        </h2>

        <div className="flex min-w-0 flex-col items-start gap-0.5">
          <RollingPrice
            value={currentPrice}
            className={`shrink-0 whitespace-nowrap text-base font-bold tracking-tight ${isDown ? 'text-down' : 'text-ink'}`}
          />
          {isDown ? (
            <span className="flex min-w-0 items-center gap-0.5 whitespace-nowrap text-[11px] font-semibold text-down">
              <TrendingDown size={11} />
              {dropRate}%
            </span>
          ) : (
            <span className="flex min-w-0 items-center gap-0.5 whitespace-nowrap text-[11px] text-muted">
              <Gavel size={11} className="shrink-0" />
              <span>
                {auction.bidCount > 0 ? `${auction.bidCount}${CARD_TEXT.bidCountSuffix}` : CARD_TEXT.noBid}
              </span>
            </span>
          )}
        </div>

        <div className="mt-auto flex flex-wrap items-center gap-1.5 pt-1">
          <div className="md:hidden">
            <AuctionTypeBadge auctionType={auction.auctionType} />
          </div>
          <span
            style={closePopStyle(justClosed)}
            className={`inline-flex h-6 shrink-0 items-center gap-1 whitespace-nowrap rounded-pill px-2 text-[11px] font-semibold ${ended ? 'bg-surface-strong text-muted' : countdown.isUrgent ? 'bg-down text-on-primary' : 'bg-surface-strong text-body'}`}
          >
            <Clock size={11} />
            {ended ? CARD_TEXT.ended : formatClock(countdown.remaining)}
            {!ended && <span className="hidden md:inline">{CARD_TEXT.remainingSuffix}</span>}
          </span>
        </div>
      </div>
    </article>
  )
}

function AuctionCardSkeleton() {
  return (
    <article
      aria-hidden
      className={`flex h-full animate-pulse gap-sm p-sm md:flex-col ${CARD_SURFACE_CLASS}`}
    >
      <div className={`relative ${THUMBNAIL_CLASS} bg-surface-strong`}>
        <div className="absolute left-2 top-2 hidden h-6 w-20 rounded-pill bg-canvas/80 md:block" />
      </div>
      <div className="flex min-w-0 flex-1 flex-col gap-1 md:mt-1 md:flex-none">
        <div className="h-3 w-2/5 rounded-pill bg-surface-strong" />
        <div className="h-3 w-1/4 rounded-pill bg-surface-strong" />
        <div className="mt-1 h-4 w-4/5 rounded-pill bg-surface-strong" />
        <div className="h-5 w-full rounded-pill bg-surface-strong" />
        <div className="h-3 w-2/5 rounded-pill bg-surface-strong" />
        <div className="mt-auto flex items-center gap-1.5 pt-1">
          <div className="h-6 w-20 rounded-pill bg-surface-strong md:hidden" />
          <div className="h-6 w-24 rounded-pill bg-surface-strong" />
        </div>
      </div>
    </article>
  )
}

export default AuctionCard
export { AuctionCardSkeleton }
