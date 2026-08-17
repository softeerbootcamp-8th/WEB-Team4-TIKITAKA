import { Clock, Flame, ImageIcon, TrendingDown, TrendingUp } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import Badge from '../components/ui/Badge'
import Button from '../components/ui/Button'
import Card from '../components/ui/Card'
import { useAuctionEvents } from '../hooks/useAuctionEvents'
import { useCountdown } from '../hooks/useCountdown'
import { useDownAuctionClock } from '../hooks/useDownAuctionClock'
import { useServerClock } from '../hooks/useServerClock'
import { requestAuctionCategories, requestAuctionList } from '../lib/api/auctions'
import type {
  AuctionCategoryOption,
  AuctionDownPricing,
  AuctionListResponse,
  AuctionSummary,
} from '../lib/api/auctions'
import type { DownPricing } from '../lib/auctionPricing'
import { formatClock, formatWon } from '../lib/format'
import {
  HOME_BANNER_ITEMS,
  HOME_BANNER_ROTATION_MS,
  HOME_BANNER_TRANSITION_MS,
  homeBannerTransitionDuration,
  nextHomeBannerIndex,
} from '../lib/homeBanner'
import { CATEGORY_QUERY_PARAM } from './auctions/constants'

const POPULAR_AUCTION_LIMIT = 5
const SPLIT_LIST_LIMIT = 4
const HOME_LIST_SIZE = 20
const UP_AUCTION_LABEL = '상향 경매'
const DOWN_AUCTION_LABEL = '하락 중'
const HOME_SKELETON_KEYS = Array.from({ length: POPULAR_AUCTION_LIMIT }, (_, index) => index)
const HOME_PANEL_SKELETON_KEYS = Array.from({ length: SPLIT_LIST_LIMIT }, (_, index) => index)
type DownAuctionSummary = AuctionSummary & {
  auctionType: 'DOWN'
  downPricing: AuctionDownPricing
}

function hasDownPricing(auction: AuctionSummary): auction is DownAuctionSummary {
  return auction.auctionType === 'DOWN' && auction.downPricing !== null
}

function toDownPricing(auction: DownAuctionSummary): DownPricing {
  return {
    startPrice: auction.startPrice,
    minimumPrice: auction.downPricing.minimumPrice,
    dropPrice: auction.downPricing.dropPrice,
    priceDropIntervalMs: auction.downPricing.priceDropIntervalMs,
    startedAt: auction.downPricing.startedAt,
  }
}

function HomePage() {
  const [response, setResponse] = useState<AuctionListResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [categories, setCategories] = useState<AuctionCategoryOption[] | null>(null)
  const [retryToken, setRetryToken] = useState(0)
  const { serverOffsetMs } = useServerClock(response?.serverTime)

  useEffect(() => {
    const controller = new AbortController()
    setIsLoading(true)
    setError(null)

    requestAuctionCategories(controller.signal).then((result) => {
      if (controller.signal.aborted) return
      setCategories(result.ok ? result.data : [])
    })

    requestAuctionList({
      keyword: '',
      auctionType: 'ALL',
      sort: 'recommended',
      page: 1,
      size: HOME_LIST_SIZE,
    }, controller.signal).then((result) => {
      if (controller.signal.aborted) return
      setIsLoading(false)
      if (result.ok) setResponse(result.data)
      else setError(result.message)
    })

    return () => controller.abort()
  }, [retryToken])

  const auctions = response?.items ?? []
  useAuctionEvents('list', auctions.map((auction) => auction.auctionId), {
    onState: (state) => {
      setResponse((current) => {
        if (!current) return current
        const items = current.items.map((auction) => {
          if (auction.auctionId !== state.auctionId || state.revision < auction.revision) {
            return auction
          }
          return {
            ...auction,
            revision: state.revision,
            status: state.status,
            currentPrice: state.currentPrice,
            bidCount: state.bidCount,
          }
        })
        return { ...current, items }
      })
    },
  })

  const popularAuctions = auctions.slice(0, POPULAR_AUCTION_LIMIT)
  const upAuctions = auctions
    .filter((auction) => auction.auctionType === 'UP')
    .slice(0, SPLIT_LIST_LIMIT)
  const downAuctions = auctions.filter(hasDownPricing).slice(0, SPLIT_LIST_LIMIT)
  const spotlight = downAuctions.length > 0
    ? downAuctions.reduce((mostDropped, auction) => {
        const progress = (auction.startPrice - auction.currentPrice)
          / (auction.startPrice - auction.downPricing.minimumPrice || 1)
        const mostDroppedProgress = (mostDropped.startPrice - mostDropped.currentPrice)
          / (mostDropped.startPrice - mostDropped.downPricing.minimumPrice || 1)
        return progress > mostDroppedProgress ? auction : mostDropped
      })
    : null

  return (
    <main className="mx-auto max-w-[1200px] px-lg py-xl">
      <HomeHeroBanner />
      <CategoryNavigation categories={categories} />

      {isLoading ? (
        <HomeSkeleton />
      ) : error ? (
        <HomeMessage message={error}>
          <Button variant="secondary" onClick={() => setRetryToken((value) => value + 1)}>
            다시 시도
          </Button>
        </HomeMessage>
      ) : auctions.length === 0 ? (
        <section className="mt-section py-xxl text-center">
          <p className="text-base text-body">현재 진행 중인 경매가 없어요.</p>
        </section>
      ) : (
        <>
          {spotlight && (
            <div className="mt-xl">
              <SpotlightBanner auction={spotlight} serverOffsetMs={serverOffsetMs} />
            </div>
          )}

          <section className="mt-xl">
            <h1 className="text-2xl font-bold text-ink">지금 인기 있는 경매 TOP 5</h1>
            <p className="mt-xs text-sm text-body">
              입찰이 활발한 경매예요. 하락 중인 경매는 시간이 지날수록 가격이 떨어집니다.
            </p>

            <div className="mt-lg grid grid-cols-2 gap-sm sm:gap-lg lg:grid-cols-3 xl:grid-cols-5">
              {popularAuctions.map((auction) => (
                <AuctionSummaryCard
                  key={auction.auctionId}
                  auction={auction}
                  serverOffsetMs={serverOffsetMs}
                />
              ))}
            </div>
          </section>

          <section className="mt-section grid grid-cols-1 gap-lg lg:grid-cols-2">
            <AuctionListPanel
              title="상승 중인 경매"
              description="입찰이 들어올수록 가격이 오르는 일반 경매예요."
              auctions={upAuctions}
              serverOffsetMs={serverOffsetMs}
            />
            <AuctionListPanel
              title="가격이 빠르게 떨어지는 중"
              description="시간이 지날수록 가격이 내려가요. 원하는 가격일 때 바로 잡으세요."
              auctions={downAuctions}
              serverOffsetMs={serverOffsetMs}
              accent
            />
          </section>
        </>
      )}
    </main>
  )
}

function HomeHeroBanner() {
  const [activeIndex, setActiveIndex] = useState(0)
  const [isRolling, setIsRolling] = useState(false)

  useEffect(() => {
    let transitionTimer: number | undefined
    const rotationTimer = window.setInterval(() => {
      setIsRolling(true)
      transitionTimer = window.setTimeout(() => {
        setActiveIndex(nextHomeBannerIndex)
        setIsRolling(false)
      }, HOME_BANNER_TRANSITION_MS)
    }, HOME_BANNER_ROTATION_MS)

    return () => {
      window.clearInterval(rotationTimer)
      window.clearTimeout(transitionTimer)
    }
  }, [])

  const nextIndex = nextHomeBannerIndex(activeIndex)

  return (
    <section
      aria-label="판매 추천 배너"
      className="relative flex min-h-[260px] items-center overflow-hidden rounded-xl bg-surface-soft px-xl py-xxl sm:min-h-[320px] sm:justify-between sm:px-[clamp(3rem,8vw,6rem)]"
    >
      <h1 className="text-[clamp(2rem,5vw,3.75rem)] font-semibold leading-[1.08] tracking-[-0.025em] text-ink">
        <span className="block">지금 판매해야 할</span>
        <span
          aria-live="polite"
          aria-atomic="true"
          className="block h-[1.04em] overflow-hidden font-bold text-primary"
        >
          <span
            className={`flex flex-col transition-transform ease-in-out motion-reduce:transition-none ${isRolling ? '-translate-y-1/2' : 'translate-y-0'}`}
            style={{ transitionDuration: homeBannerTransitionDuration(isRolling) }}
          >
            <span className="h-[1.04em] shrink-0">“{HOME_BANNER_ITEMS[activeIndex].label}”</span>
            <span aria-hidden="true" className="h-[1.04em] shrink-0">
              “{HOME_BANNER_ITEMS[nextIndex].label}”
            </span>
          </span>
        </span>
        <span className="block">비드윈에서</span>
      </h1>

      <span
        aria-hidden="true"
        className="absolute bottom-lg right-lg block h-[1.15em] overflow-hidden text-5xl leading-none sm:static sm:shrink-0 sm:text-[clamp(5rem,10vw,7rem)]"
      >
        <span
          className={`flex flex-col transition-transform ease-in-out motion-reduce:transition-none ${isRolling ? '-translate-y-1/2' : 'translate-y-0'}`}
          style={{ transitionDuration: homeBannerTransitionDuration(isRolling) }}
        >
          <span className="flex h-[1.15em] shrink-0 items-center justify-center">
            {HOME_BANNER_ITEMS[activeIndex].emoji}
          </span>
          <span className="flex h-[1.15em] shrink-0 items-center justify-center">
            {HOME_BANNER_ITEMS[nextIndex].emoji}
          </span>
        </span>
      </span>
    </section>
  )
}

function CategoryNavigation({
  categories,
}: {
  categories: AuctionCategoryOption[] | null
}) {
  return (
    <section className="mt-xl" aria-labelledby="home-category-title">
      <h2 id="home-category-title" className="text-lg font-bold text-ink">
        카테고리로 둘러보기
      </h2>
      <div className="mt-base flex flex-wrap gap-sm">
        {categories === null && (
          <span className="text-sm text-muted">카테고리를 불러오는 중…</span>
        )}
        {categories?.map((category) => (
          <Link
            key={category.code}
            to={`/auctions?${CATEGORY_QUERY_PARAM}=${category.code}`}
            className="rounded-pill border border-hairline-strong bg-canvas px-lg py-sm text-sm font-semibold text-body transition-colors hover:border-primary hover:bg-primary hover:text-on-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
          >
            {category.label}
          </Link>
        ))}
        {categories?.length === 0 && (
          <span className="text-sm text-muted">사용 가능한 카테고리가 없어요.</span>
        )}
      </div>
    </section>
  )
}

function HomeMessage({ message, children }: { message: string; children?: React.ReactNode }) {
  return (
    <section className="mt-section flex flex-col items-center justify-center gap-base py-xxl text-center">
      <p className="text-base text-body">{message}</p>
      {children}
    </section>
  )
}

function SpotlightBanner({
  auction,
  serverOffsetMs,
}: {
  auction: DownAuctionSummary
  serverOffsetMs: number
}) {
  const pricing = useMemo(() => toDownPricing(auction), [auction])
  const { currentPrice, remaining, isUrgent } = useDownAuctionClock(pricing, serverOffsetMs)

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
            <div key={currentPrice} className="whitespace-nowrap text-[clamp(1.25rem,5vw,1.5rem)] font-bold tracking-tight text-down">
              {formatWon(currentPrice)}
            </div>
            <div className={`flex items-center justify-end gap-1 text-sm font-semibold ${isUrgent ? 'text-down' : 'text-body'}`}>
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

function AuctionSummaryCard({
  auction,
  serverOffsetMs,
}: {
  auction: AuctionSummary
  serverOffsetMs: number
}) {
  return hasDownPricing(auction) ? (
    <DownSummaryCard auction={auction} serverOffsetMs={serverOffsetMs} />
  ) : (
    <StaticSummaryCard auction={auction} serverOffsetMs={serverOffsetMs} />
  )
}

function StaticSummaryCard({
  auction,
  serverOffsetMs,
}: {
  auction: AuctionSummary
  serverOffsetMs: number
}) {
  const deadline = useMemo(
    () => auction.deadline - serverOffsetMs,
    [auction.deadline, serverOffsetMs],
  )
  const { remaining, isUrgent } = useCountdown(deadline)
  return (
    <SummaryCardView
      auction={auction}
      price={auction.currentPrice}
      remaining={remaining}
      isUrgent={isUrgent}
    />
  )
}

function DownSummaryCard({
  auction,
  serverOffsetMs,
}: {
  auction: DownAuctionSummary
  serverOffsetMs: number
}) {
  const pricing = useMemo(() => toDownPricing(auction), [auction])
  const { currentPrice, remaining, isUrgent } = useDownAuctionClock(pricing, serverOffsetMs)
  return (
    <SummaryCardView
      auction={auction}
      price={currentPrice}
      remaining={remaining}
      isUrgent={isUrgent}
    />
  )
}

function SummaryCardView({
  auction,
  price,
  remaining,
  isUrgent,
}: {
  auction: AuctionSummary
  price: number
  remaining: number
  isUrgent: boolean
}) {
  const isDown = auction.auctionType === 'DOWN'
  const priceText = formatWon(price)
  return (
    <Link to={`/auctions/${auction.auctionId}`} className="block">
      <Card className="flex h-full flex-col gap-sm hover:shadow-soft">
        <AuctionThumbnail url={auction.thumbnailUrl} />
        <Badge tone={isDown ? 'danger' : 'neutral'}>
          {isDown ? <TrendingDown size={12} /> : <TrendingUp size={12} />}
          {isDown ? DOWN_AUCTION_LABEL : UP_AUCTION_LABEL}
        </Badge>
        <h2 className="line-clamp-2 text-sm font-semibold text-ink">
          {auction.title}
        </h2>
        <div className="flex min-w-0 flex-col gap-1">
          <span className={`whitespace-nowrap font-bold tracking-tight ${priceText.length > 14 ? 'text-xs' : 'text-sm'} ${isDown ? 'text-down' : 'text-ink'}`}>
            {priceText}
          </span>
          <span className={`flex items-center gap-1 text-xs ${isUrgent ? 'text-down' : 'text-muted'}`}>
            <Clock size={12} />
            {formatClock(remaining)}
          </span>
        </div>
      </Card>
    </Link>
  )
}

function AuctionThumbnail({ url, compact = false }: { url: string | null; compact?: boolean }) {
  const sizeClass = compact ? 'h-11 w-11' : 'aspect-square w-full'
  return url ? (
    <img src={url} alt="" className={`${sizeClass} shrink-0 rounded-md object-cover`} />
  ) : (
    <span className={`${sizeClass} flex shrink-0 items-center justify-center rounded-md bg-surface-soft text-muted`}>
      <ImageIcon size={compact ? 16 : 24} />
    </span>
  )
}

function AuctionListPanel({
  title,
  description,
  auctions,
  serverOffsetMs,
  accent,
}: {
  title: string
  description: string
  auctions: AuctionSummary[]
  serverOffsetMs: number
  accent?: boolean
}) {
  return (
    <div className="rounded-xl border border-hairline-soft bg-canvas p-lg">
      <h2 className="text-lg font-bold text-ink">{title}</h2>
      <p className="mt-xs text-sm text-body">{description}</p>
      {auctions.length === 0 ? (
        <p className="py-xl text-center text-sm text-muted">표시할 경매가 없어요.</p>
      ) : (
        <ul className="mt-base flex flex-col divide-y divide-hairline-soft">
          {auctions.map((auction) => (
            <AuctionListRow
              key={auction.auctionId}
              auction={auction}
              serverOffsetMs={serverOffsetMs}
              accent={accent}
            />
          ))}
        </ul>
      )}
    </div>
  )
}

function AuctionListRow({
  auction,
  serverOffsetMs,
  accent,
}: {
  auction: AuctionSummary
  serverOffsetMs: number
  accent?: boolean
}) {
  return hasDownPricing(auction) ? (
    <DownListRow auction={auction} serverOffsetMs={serverOffsetMs} accent={accent} />
  ) : (
    <StaticListRow auction={auction} serverOffsetMs={serverOffsetMs} accent={accent} />
  )
}

function StaticListRow({
  auction,
  serverOffsetMs,
  accent,
}: {
  auction: AuctionSummary
  serverOffsetMs: number
  accent?: boolean
}) {
  const deadline = useMemo(
    () => auction.deadline - serverOffsetMs,
    [auction.deadline, serverOffsetMs],
  )
  const { remaining, isUrgent } = useCountdown(deadline)
  return (
    <ListRowView
      auction={auction}
      accent={accent}
      price={auction.currentPrice}
      remaining={remaining}
      isUrgent={isUrgent}
    />
  )
}

function DownListRow({
  auction,
  serverOffsetMs,
  accent,
}: {
  auction: DownAuctionSummary
  serverOffsetMs: number
  accent?: boolean
}) {
  const pricing = useMemo(() => toDownPricing(auction), [auction])
  const { currentPrice, remaining, isUrgent } = useDownAuctionClock(pricing, serverOffsetMs)
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

function ListRowView({
  auction,
  accent,
  price,
  remaining,
  isUrgent,
}: {
  auction: AuctionSummary
  accent?: boolean
  price: number
  remaining: number
  isUrgent: boolean
}) {
  return (
    <li>
      <Link to={`/auctions/${auction.auctionId}`} className="flex items-center gap-sm py-sm first:pt-0 last:pb-0">
        <AuctionThumbnail url={auction.thumbnailUrl} compact />
        <span className="line-clamp-1 flex-1 text-sm font-medium text-ink">{auction.title}</span>
        <span className={`shrink-0 whitespace-nowrap text-sm font-bold ${accent ? 'text-down' : 'text-ink'}`}>
          {formatWon(price)}
        </span>
        <span className={`flex shrink-0 items-center gap-1 text-xs ${isUrgent ? 'text-down' : 'text-muted'}`}>
          <Clock size={11} />
          {formatClock(remaining)}
        </span>
      </Link>
    </li>
  )
}

function HomeSkeleton() {
  return (
    <div
      role="status"
      aria-label="홈 경매를 불러오는 중"
    >
      <span className="sr-only">홈 경매를 불러오는 중…</span>
      <section className="mt-section motion-safe:animate-pulse">
        <div className="h-8 w-64 rounded-pill bg-surface-strong" />
        <div className="mt-sm h-4 w-96 max-w-full rounded-pill bg-surface-strong" />
        <div className="mt-lg grid grid-cols-2 gap-sm sm:gap-lg lg:grid-cols-3 xl:grid-cols-5">
          {HOME_SKELETON_KEYS.map((key) => (
            <Card key={key} className="flex h-full flex-col gap-sm">
              <div className="aspect-square w-full rounded-md bg-surface-strong" />
              <div className="h-6 w-2/3 rounded-pill bg-surface-strong" />
              <div className="h-4 w-4/5 rounded-pill bg-surface-strong" />
              <div className="h-4 w-full rounded-pill bg-surface-strong" />
              <div className="h-3 w-16 rounded-pill bg-surface-strong" />
            </Card>
          ))}
        </div>
      </section>

      <section className="mt-section grid grid-cols-1 gap-lg lg:grid-cols-2">
        {[0, 1].map((panelKey) => (
          <div key={panelKey} className="motion-safe:animate-pulse rounded-xl border border-hairline-soft bg-canvas p-lg">
            <div className="h-6 w-40 rounded-pill bg-surface-strong" />
            <div className="mt-sm h-4 w-3/4 rounded-pill bg-surface-strong" />
            <div className="mt-base flex flex-col gap-sm">
              {HOME_PANEL_SKELETON_KEYS.map((key) => (
                <div key={key} className="flex items-center gap-sm py-xs">
                  <div className="h-11 w-11 shrink-0 rounded-md bg-surface-strong" />
                  <div className="h-4 flex-1 rounded-pill bg-surface-strong" />
                  <div className="h-4 w-20 rounded-pill bg-surface-strong" />
                  <div className="h-3 w-14 rounded-pill bg-surface-strong" />
                </div>
              ))}
            </div>
          </div>
        ))}
      </section>
    </div>
  )
}

export default HomePage
