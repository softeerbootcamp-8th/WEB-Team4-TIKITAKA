import {
  BadgeCheck,
  Clock,
  Flag,
  Heart,
  ImageOff,
  ShieldCheck,
  TrendingDown,
  Truck,
  WifiOff,
} from 'lucide-react'
import type { ChangeEvent } from 'react'
import { useEffect, useRef, useState } from 'react'
import {
  createSearchParams,
  useLocation,
  useNavigate,
  useParams,
} from 'react-router-dom'
import Badge from '../../../components/ui/Badge'
import type { BadgeTone } from '../../../components/ui/Badge'
import Button from '../../../components/ui/Button'
import Card from '../../../components/ui/Card'
import TextInput from '../../../components/ui/TextInput'
import { useAuctionEvents } from '../../../hooks/useAuctionEvents'
import type { ConnectionStatus } from '../../../hooks/useAuctionEvents'
import { useAuth } from '../../../hooks/useAuth'
import { useCountdown } from '../../../hooks/useCountdown'
import { useDownAuctionClock } from '../../../hooks/useDownAuctionClock'
import { useServerClock } from '../../../hooks/useServerClock'
import { useToast } from '../../../hooks/useToast'
import {
  requestAuctionDetail,
  requestBid,
  requestBidHistory,
  requestBuyNow,
} from '../../../lib/api/auctions'
import type {
  AuctionDetail,
  AuctionSeller,
  AuctionStatus,
  BidHistoryItem,
  BidType,
  DownAuctionDetail,
  UpAuctionDetail,
} from '../../../lib/api/auctions'
import type { ApiFailure } from '../../../lib/api/client'
import { computeCurrentDownPrice, computeDropHistory } from '../../../lib/auctionPricing'
import { formatClock, formatTimeOfDay, formatWon } from '../../../lib/format'

const CATEGORY_LABEL = {
  HOUSEHOLD: '생활용품',
  FOOD: '먹거리',
  FURNITURE: '가구',
} as const

const STATUS_LABEL: Record<AuctionStatus, string> = {
  OPEN: '진행 중',
  BID_ONGOING: '입찰 진행 중',
  WINNER_DETERMINING: '낙찰자 선정 중',
  COMPLETED: '거래 확정',
  UNSOLD: '유찰',
}

const STATUS_BADGE_TONE: Record<AuctionStatus, BadgeTone> = {
  OPEN: 'live',
  BID_ONGOING: 'live',
  WINNER_DETERMINING: 'primary',
  COMPLETED: 'success',
  UNSOLD: 'ended',
}

const TRADE_LABEL = {
  DELIVERY: '택배 거래',
  DIRECT: '직거래',
} as const

const BID_UNIT = 1_000
const BID_INCREMENT_OPTIONS = [1_000, 10_000, 50_000] as const
const BID_HISTORY_LIMIT = 10

type ActionKind = 'bid' | 'buy' | null

function isOngoing(status: AuctionStatus) {
  return status === 'OPEN' || status === 'BID_ONGOING'
}

function isTerminal(status: AuctionStatus) {
  return status === 'COMPLETED' || status === 'UNSOLD'
}

function mergeBidHistory(
  current: BidHistoryItem[],
  incoming: BidHistoryItem[],
): BidHistoryItem[] {
  const entries = new Map(current.map((bid) => [bid.entryId, bid]))
  incoming.forEach((bid) => entries.set(bid.entryId, bid))
  return [...entries.values()]
    .sort((left, right) => (
      right.biddedAt - left.biddedAt || right.entryId.localeCompare(left.entryId)
    ))
    .slice(0, BID_HISTORY_LIMIT)
}

function createIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `buy-now-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function AuctionDetailPage() {
  const { auctionId: rawAuctionId } = useParams()
  const auctionId = Number(rawAuctionId)
  const validAuctionId = Number.isSafeInteger(auctionId) && auctionId > 0
  const [auction, setAuction] = useState<AuctionDetail | null>(null)
  const [bidHistory, setBidHistory] = useState<BidHistoryItem[]>([])
  const [historyError, setHistoryError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(validAuctionId)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [notFound, setNotFound] = useState(!validAuctionId)
  const [retryToken, setRetryToken] = useState(0)
  const [pendingAction, setPendingAction] = useState<ActionKind>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [ownBidEntryIds, setOwnBidEntryIds] = useState<ReadonlySet<string>>(
    () => new Set(),
  )
  const [hasSubmittedSealedBid, setHasSubmittedSealedBid] = useState(false)
  const buyNowKeyRef = useRef<string | null>(null)
  const { isAuthenticated, setAuthenticated } = useAuth()
  const { showToast } = useToast()
  const navigate = useNavigate()
  const location = useLocation()
  const serverOffsetMs = useServerClock(auction?.serverTime)

  useEffect(() => {
    if (!validAuctionId) {
      setNotFound(true)
      setIsLoading(false)
      return
    }

    const controller = new AbortController()
    let active = true

    setIsLoading(true)
    setLoadError(null)
    setHistoryError(null)
    setNotFound(false)

    Promise.all([
      requestAuctionDetail(auctionId, controller.signal),
      requestBidHistory(auctionId, controller.signal),
    ]).then(([detailResult, historyResult]) => {
      if (!active) return
      setIsLoading(false)

      if (!detailResult.ok) {
        setAuction(null)
        if (detailResult.status === 404) {
          setNotFound(true)
        } else {
          setLoadError(detailResult.message)
        }
        return
      }

      setAuction(detailResult.data)

      if (historyResult.ok) {
        setBidHistory((current) => mergeBidHistory(current, historyResult.data.bidLog))
      } else if (detailResult.data.auctionType === 'UP') {
        setHistoryError(historyResult.message)
      }
    })

    return () => {
      active = false
      controller.abort()
    }
  }, [auctionId, retryToken, validAuctionId])

  useEffect(() => {
    setOwnBidEntryIds(new Set())
    setHasSubmittedSealedBid(false)
    setBidHistory([])
    setPendingAction(null)
    setActionError(null)
    buyNowKeyRef.current = null
  }, [auctionId])

  const { status: connectionStatus, reconnect } = useAuctionEvents(
    'detail',
    auction ? [auction.auctionId] : [],
    {
      onState: (state) => {
        setAuction((current) => {
          if (
            !current
            || state.auctionId !== current.auctionId
            || state.auctionType !== current.auctionType
            || state.revision < current.revision
          ) {
            return current
          }

          if (current.auctionType === 'UP') {
            return {
              ...current,
              status: state.status,
              revision: state.revision,
              currentPrice: state.currentPrice,
              bidCount: state.bidCount,
            }
          }

          return {
            ...current,
            status: state.status,
            revision: state.revision,
            finalPrice: isTerminal(state.status)
              ? state.currentPrice
              : current.finalPrice,
          }
        })
      },
      onBidCreated: (bid) => {
        setHistoryError(null)
        setBidHistory((current) => mergeBidHistory(current, [bid]))
      },
      onBidHistorySnapshot: (history) => {
        setHistoryError(null)
        setBidHistory((current) => mergeBidHistory(current, history.bidLog))
        setAuction((current) => (
          current?.auctionType === 'UP' && history.bidCount > current.bidCount
            ? { ...current, bidCount: history.bidCount }
            : current
        ))
      },
    },
  )

  function redirectToLogin() {
    const next = `${location.pathname}${location.search}`
    navigate({
      pathname: '/login',
      search: `?${createSearchParams({ next })}`,
    })
  }

  function ensureAuthenticated() {
    if (isAuthenticated === true) return true
    if (isAuthenticated === false) redirectToLogin()
    return false
  }

  function handleActionFailure(failure: ApiFailure) {
    setActionError(failure.message)
    if (failure.status === 401) {
      setAuthenticated(false)
      redirectToLogin()
    }
  }

  function refreshBidHistory() {
    void requestBidHistory(auctionId).then((result) => {
      if (!result.ok) {
        setHistoryError(result.message)
        return
      }
      setHistoryError(null)
      setBidHistory((current) => mergeBidHistory(current, result.data.bidLog))
      setAuction((current) => current?.auctionType === 'UP'
        ? { ...current, bidCount: Math.max(current.bidCount, result.data.bidCount) }
        : current)
    })
  }

  async function handleBid(price: number, bidType: BidType) {
    if (!ensureAuthenticated() || pendingAction !== null) return false

    setPendingAction('bid')
    setActionError(null)
    const result = await requestBid(auctionId, { price, bidType })
    setPendingAction(null)

    if (!result.ok) {
      handleActionFailure(result)
      return false
    }

    const entryId = result.data.status === 'SEALED'
      ? `SEALED:${result.data.bidId}`
      : `BID:${result.data.bidId}`
    setOwnBidEntryIds((current) => new Set(current).add(entryId))

    if (result.data.status === 'SEALED') {
      setHasSubmittedSealedBid(true)
      showToast('밀봉 입찰을 제출했어요. 마감 후 결과가 공개됩니다.', 'success')
    } else {
      const acceptedPrice = result.data.price
      setAuction((current) => current?.auctionType === 'UP'
        ? { ...current, currentPrice: Math.max(current.currentPrice, acceptedPrice) }
        : current)
      refreshBidHistory()
      showToast(`${formatWon(acceptedPrice)}으로 입찰했어요.`, 'success')
    }
    return true
  }

  async function handleBuyNow() {
    if (
      !auction
      || !ensureAuthenticated()
      || pendingAction !== null
    ) {
      return
    }

    const key = buyNowKeyRef.current ?? createIdempotencyKey()
    buyNowKeyRef.current = key
    setPendingAction('buy')
    setActionError(null)
    const result = await requestBuyNow(auction.auctionType, auction.auctionId, key)
    setPendingAction(null)

    if (!result.ok) {
      // 전송 여부를 알 수 없는 네트워크 실패만 같은 키로 재시도한다.
      if (result.status !== null) buyNowKeyRef.current = null
      handleActionFailure(result)
      return
    }

    buyNowKeyRef.current = null
    setAuction((current) => {
      if (!current) return current
      if (current.auctionType === 'UP') {
        return {
          ...current,
          status: 'COMPLETED',
          currentPrice: result.data.finalPrice,
        }
      }
      return {
        ...current,
        status: 'COMPLETED',
        finalPrice: result.data.finalPrice,
      }
    })
    showToast(`${formatWon(result.data.finalPrice)}에 구매가 확정됐어요.`, 'success')
  }

  if (!validAuctionId || notFound) return <NotFoundState />

  if (isLoading) {
    return (
      <main className="mx-auto flex max-w-[1200px] items-center justify-center px-lg py-section text-sm text-muted">
        경매를 불러오는 중…
      </main>
    )
  }

  if (loadError || !auction) {
    return (
      <LoadErrorState
        message={loadError ?? '경매 정보를 불러오지 못했습니다.'}
        onRetry={() => setRetryToken((value) => value + 1)}
      />
    )
  }

  return (
    <>
      <main className="mx-auto max-w-[1200px] px-lg py-xl">
        <AuctionHeader
          auction={auction}
          connectionStatus={connectionStatus}
        />

        <div className="mt-lg grid grid-cols-1 gap-xl lg:grid-cols-[1fr_380px]">
          <div className="order-2 flex flex-col gap-xl lg:order-1">
            <AuctionGallery images={auction.images} title={auction.title} />
            <ProductTabs auction={auction} />
            {auction.auctionType === 'UP' ? (
              <BidHistoryPanel
                bidCount={auction.bidCount}
                bidLog={bidHistory}
                ownBidEntryIds={ownBidEntryIds}
                sealedBidActive={
                  isOngoing(auction.status)
                  && Date.now() + serverOffsetMs >= auction.sealedBidStartsAt
                  && Date.now() + serverOffsetMs < auction.deadline
                }
                error={historyError}
                onRetry={() => setRetryToken((value) => value + 1)}
              />
            ) : (
              <PriceDropTimeline auction={auction} serverOffsetMs={serverOffsetMs} />
            )}
          </div>

          <div className="order-1 lg:sticky lg:top-[88px] lg:order-2 lg:max-h-[calc(100dvh-104px)] lg:self-start lg:overflow-y-auto">
            {auction.auctionType === 'UP' ? (
              <UpBidPanel
                auction={auction}
                serverOffsetMs={serverOffsetMs}
                pendingAction={pendingAction}
                actionError={actionError}
                hasSubmittedSealedBid={hasSubmittedSealedBid}
                authPending={isAuthenticated === null}
                onClearError={() => setActionError(null)}
                onBid={handleBid}
                onBuyNow={handleBuyNow}
              />
            ) : (
              <DownBuyPanel
                auction={auction}
                serverOffsetMs={serverOffsetMs}
                pendingAction={pendingAction}
                actionError={actionError}
                authPending={isAuthenticated === null}
                onClearError={() => setActionError(null)}
                onBuyNow={handleBuyNow}
              />
            )}
          </div>
        </div>
      </main>
      {connectionStatus === 'disconnected' && <ReconnectDialog onReconnect={reconnect} />}
    </>
  )
}

function ReconnectDialog({ onReconnect }: { onReconnect: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/70 px-lg">
      <Card
        role="dialog"
        aria-modal="true"
        aria-labelledby="reconnect-title"
        className="flex w-full max-w-[420px] flex-col items-center gap-lg text-center shadow-soft"
      >
        <span className="flex h-14 w-14 items-center justify-center rounded-full bg-down-tint text-down">
          <WifiOff size={24} />
        </span>
        <div>
          <h2 id="reconnect-title" className="text-xl font-bold text-ink">
            실시간 연결이 끊겼어요
          </h2>
          <p className="mt-xs text-sm leading-relaxed text-body">
            최신 경매 시간과 가격을 확인하려면 다시 연결해주세요.
          </p>
        </div>
        <Button size="lg" className="w-full" onClick={onReconnect} autoFocus>
          다시 연결하기
        </Button>
      </Card>
    </div>
  )
}

function NotFoundState() {
  const navigate = useNavigate()

  return (
    <main className="mx-auto flex max-w-[1200px] flex-col items-center gap-base px-lg py-section text-center">
      <p className="text-lg font-semibold text-ink">경매를 찾을 수 없어요.</p>
      <p className="text-sm text-body">삭제되었거나 잘못된 주소예요.</p>
      <Button variant="secondary" onClick={() => navigate('/auctions')}>
        경매 목록으로 돌아가기
      </Button>
    </main>
  )
}

function LoadErrorState({
  message,
  onRetry,
}: {
  message: string
  onRetry: () => void
}) {
  return (
    <main className="mx-auto flex max-w-[1200px] flex-col items-center gap-base px-lg py-section text-center">
      <p className="text-lg font-semibold text-ink">경매를 불러오지 못했어요.</p>
      <p className="text-sm text-body">{message}</p>
      <Button variant="secondary" onClick={onRetry}>다시 시도</Button>
    </main>
  )
}

function EmptyState({ message }: { message: string }) {
  return <p className="py-xl text-center text-sm text-muted">{message}</p>
}

function AuctionHeader({
  auction,
  connectionStatus,
}: {
  auction: AuctionDetail
  connectionStatus: ConnectionStatus
}) {
  const [interested, setInterested] = useState(false)
  const liveLabel = connectionStatus === 'open'
    ? '실시간 연결됨'
    : connectionStatus === 'reconnecting'
      ? '실시간 재연결 중'
      : connectionStatus === 'disconnected'
        ? '실시간 연결 끊김'
        : '실시간 연결 중'

  return (
    <div className="flex flex-col gap-sm">
      <span className="text-xs text-muted">{CATEGORY_LABEL[auction.category]}</span>

      <div className="flex flex-wrap items-start justify-between gap-sm">
        <div>
          <div className="flex flex-wrap items-center gap-xs">
            <Badge tone={STATUS_BADGE_TONE[auction.status]}>
              {STATUS_LABEL[auction.status]}
            </Badge>
            <span className="text-xs text-muted" aria-live="polite">{liveLabel}</span>
          </div>
          <h1 className="mt-xs text-2xl font-bold text-ink">{auction.title}</h1>
        </div>

        <div className="flex shrink-0 gap-xs">
          <button
            type="button"
            onClick={() => setInterested((current) => !current)}
            aria-pressed={interested}
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

  return (
    <div className="flex flex-col gap-sm">
      <div className="flex aspect-square items-center justify-center overflow-hidden rounded-xl bg-surface-soft">
        {!hasImages || broken[active] ? (
          <div className="flex flex-col items-center gap-xs text-muted">
            <ImageOff size={32} />
            <span className="text-xs">
              {hasImages ? '이미지를 불러오지 못했어요' : '등록된 이미지가 없어요'}
            </span>
          </div>
        ) : (
          <img
            src={images[active]}
            alt={title}
            className="h-full w-full object-cover"
            onError={() => setBroken((current) => ({ ...current, [active]: true }))}
          />
        )}
      </div>

      {hasImages && (
        <div className="flex gap-sm overflow-x-auto">
          {images.map((src, index) => (
            <button
              key={`${src}-${index}`}
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
                  onError={() => setBroken((current) => ({ ...current, [index]: true }))}
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
        {active === '상품 정보' && (
          <p className="whitespace-pre-line">{auction.description}</p>
        )}
        {active === '배송·거래' && (
          <div className="flex items-start gap-sm">
            <Truck size={16} className="mt-0.5 shrink-0 text-muted" />
            <p>{TRADE_LABEL[auction.tradeType]}</p>
          </div>
        )}
        {active === '판매자 정보' && <SellerInfo seller={auction.seller} />}
      </div>
    </Card>
  )
}

function SellerInfo({ seller }: { seller: AuctionSeller }) {
  return (
    <div className="flex items-center gap-sm">
      {seller.profileImageUrl ? (
        <img
          src={seller.profileImageUrl}
          alt=""
          className="h-11 w-11 shrink-0 rounded-full object-cover"
        />
      ) : (
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-surface-strong text-base font-bold text-body">
          {seller.name.slice(0, 1)}
        </span>
      )}
      <div>
        <div className="flex items-center gap-1 font-semibold text-ink">
          {seller.name}
          {seller.verified && <BadgeCheck size={14} className="text-primary" />}
        </div>
        <p className="text-xs text-muted">완료 거래 {seller.dealCount}회</p>
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
  serverOffsetMs: number
  pendingAction: ActionKind
  actionError: string | null
  hasSubmittedSealedBid: boolean
  authPending: boolean
  onClearError: () => void
  onBid: (amount: number, bidType: BidType) => Promise<boolean>
  onBuyNow: () => Promise<void>
}

function UpBidPanel({
  auction,
  serverOffsetMs,
  pendingAction,
  actionError,
  hasSubmittedSealedBid,
  authPending,
  onClearError,
  onBid,
  onBuyNow,
}: UpBidPanelProps) {
  const deadline = useCountdown(auction.deadline - serverOffsetMs)
  const sealedStart = useCountdown(auction.sealedBidStartsAt - serverOffsetMs)
  const nextMinBid = auction.currentPrice + BID_UNIT
  const [amount, setAmount] = useState(nextMinBid)
  const [inputError, setInputError] = useState<string | null>(null)
  const ended = deadline.isEnded || !isOngoing(auction.status)
  const sealedBidActive = !ended && sealedStart.isEnded
  const canBuyNow = (
    !ended
    && !sealedBidActive
    && auction.status === 'OPEN'
    && auction.buyNowPrice !== null
  )
  const busy = pendingAction !== null

  function handleChip(increment: number) {
    setAmount((current) => Math.max(current, auction.currentPrice) + increment)
    setInputError(null)
    onClearError()
  }

  function handleAmountChange(event: ChangeEvent<HTMLInputElement>) {
    const raw = event.target.value.replace(/[^0-9]/g, '').slice(0, 15)
    setAmount(raw === '' ? 0 : Number(raw))
    setInputError(null)
    onClearError()
  }

  async function handleSubmit() {
    if (!Number.isSafeInteger(amount) || amount < nextMinBid) {
      setInputError(`최소 ${formatWon(nextMinBid)} 이상 입찰해주세요.`)
      return
    }
    if (amount % BID_UNIT !== 0) {
      setInputError('입찰 금액은 1,000원 단위로 입력해주세요.')
      return
    }

    const accepted = await onBid(amount, sealedBidActive ? 'SEALED' : 'OPEN')
    if (accepted && !sealedBidActive) setAmount(amount + BID_UNIT)
  }

  return (
    <Card className="flex flex-col gap-lg p-lg">
      <div className="flex flex-wrap items-center gap-xs">
        <Badge tone={ended ? 'ended' : sealedBidActive ? 'primary' : 'live'}>
          {ended ? '마감' : sealedBidActive ? '밀봉 입찰' : '진행 중'}
        </Badge>
        {auction.seller.verified && (
          <Badge tone="muted">
            <ShieldCheck size={13} />
            판매자 인증
          </Badge>
        )}
      </div>

      <div>
        <p className="text-xs text-muted">현재 공개 최고가</p>
        <p className="text-3xl font-bold text-ink">{formatWon(auction.currentPrice)}</p>
        <p className={`mt-1 flex items-center gap-1 text-sm font-semibold ${
          deadline.isUrgent ? 'text-down' : 'text-body'
        }`}>
          <Clock size={14} />
          {ended ? '경매 종료' : `${formatClock(deadline.remaining)} 남음`}
        </p>
      </div>

      <div className="grid grid-cols-2 gap-sm border-y border-hairline-soft py-base text-sm">
        <Stat label="입찰 수" value={`${auction.bidCount.toLocaleString('ko-KR')}회`} />
        <Stat label="시작가" value={formatWon(auction.startPrice)} />
        <Stat
          label="즉시구매가"
          value={auction.buyNowPrice === null ? '미설정' : formatWon(auction.buyNowPrice)}
        />
        <Stat label="거래 방식" value={TRADE_LABEL[auction.tradeType]} />
      </div>

      {!ended && (
        <>
          {sealedBidActive && (
            <p className="rounded-md bg-primary-tint p-sm text-xs leading-relaxed text-primary">
              입찰 금액은 마감 전까지 공개되지 않으며 한 번만 제출할 수 있어요.
            </p>
          )}

          <div className="flex gap-sm">
            {BID_INCREMENT_OPTIONS.map((increment) => (
              <button
                key={increment}
                type="button"
                onClick={() => handleChip(increment)}
                disabled={busy || hasSubmittedSealedBid && sealedBidActive}
                className="flex-1 rounded-pill border border-hairline py-sm text-sm font-semibold text-body hover:bg-surface-strong disabled:cursor-not-allowed disabled:opacity-60"
              >
                +{increment.toLocaleString('ko-KR')}
              </button>
            ))}
          </div>

          <TextInput
            label={sealedBidActive ? '밀봉 입찰 금액' : '입찰 금액'}
            inputMode="numeric"
            value={amount === 0 ? '' : amount.toLocaleString('ko-KR')}
            onChange={handleAmountChange}
            error={inputError ?? actionError ?? undefined}
            disabled={busy || authPending || hasSubmittedSealedBid && sealedBidActive}
          />

          <Button
            size="lg"
            onClick={handleSubmit}
            disabled={busy || authPending || hasSubmittedSealedBid && sealedBidActive}
          >
            {pendingAction === 'bid'
              ? '입찰 처리 중…'
              : hasSubmittedSealedBid && sealedBidActive
                ? '밀봉 입찰 제출 완료'
                : `${formatWon(amount)}으로 입찰하기`}
          </Button>

          {canBuyNow && (
            <Button
              variant="secondary"
              onClick={onBuyNow}
              disabled={busy || authPending}
            >
              {pendingAction === 'buy'
                ? '구매 처리 중…'
                : `즉시구매 ${formatWon(auction.buyNowPrice!)}`}
            </Button>
          )}
        </>
      )}
    </Card>
  )
}

interface DownBuyPanelProps {
  auction: DownAuctionDetail
  serverOffsetMs: number
  pendingAction: ActionKind
  actionError: string | null
  authPending: boolean
  onClearError: () => void
  onBuyNow: () => Promise<void>
}

function DownBuyPanel({
  auction,
  serverOffsetMs,
  pendingAction,
  actionError,
  authPending,
  onClearError,
  onBuyNow,
}: DownBuyPanelProps) {
  const clock = useDownAuctionClock(auction, serverOffsetMs)
  const deadline = useCountdown(auction.deadline - serverOffsetMs)
  const ended = deadline.isEnded || auction.status !== 'OPEN'
  const currentPrice = ended && auction.finalPrice !== null
    ? auction.finalPrice
    : ended
      ? computeCurrentDownPrice(auction, auction.deadline)
      : clock.currentPrice

  return (
    <Card className="flex flex-col gap-lg p-lg">
      <div className="flex flex-wrap items-center gap-xs">
        <Badge tone={ended ? 'ended' : 'live'}>{ended ? '마감' : '하락 중'}</Badge>
        {auction.seller.verified && (
          <Badge tone="muted">
            <ShieldCheck size={13} />
            판매자 인증
          </Badge>
        )}
      </div>

      <div>
        <p className="text-xs text-muted">
          {ended && auction.status === 'COMPLETED' ? '최종 구매가' : '지금 이 가격'}
        </p>
        <p className="flex items-center gap-1.5">
          <span className="text-3xl font-bold text-down">{formatWon(currentPrice)}</span>
          {!ended && !clock.atFloor && <TrendingDown size={20} className="text-down" />}
        </p>
        {!ended && !clock.atFloor && (
          <p className={`mt-1 flex items-center gap-1 text-sm font-semibold ${
            clock.isUrgent ? 'text-down' : 'text-body'
          }`}>
            <Clock size={14} />
            {formatClock(clock.remaining)} 후 추가 하락
          </p>
        )}
        {!ended && clock.atFloor && (
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
        <Stat label="하락 폭" value={formatWon(auction.dropPrice)} />
        <Stat label="거래 방식" value={TRADE_LABEL[auction.tradeType]} />
      </div>

      {!ended && (
        <>
          <Button
            size="lg"
            onClick={() => {
              onClearError()
              void onBuyNow()
            }}
            disabled={pendingAction !== null || authPending}
          >
            {pendingAction === 'buy'
              ? '구매 처리 중…'
              : `${formatWon(currentPrice)}에 구매하기`}
          </Button>
          {actionError && <p className="text-sm text-down">{actionError}</p>}
          <p className="text-center text-xs text-muted">
            서버의 구매 확정 시각에 계산된 가격으로 선착순 거래가 확정됩니다.
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
  const percent = totalRange > 0
    ? Math.min(100, Math.max(0, (dropped / totalRange) * 100))
    : 0

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

function BidHistoryPanel({
  bidCount,
  bidLog,
  ownBidEntryIds,
  sealedBidActive,
  error,
  onRetry,
}: {
  bidCount: number
  bidLog: BidHistoryItem[]
  ownBidEntryIds: ReadonlySet<string>
  sealedBidActive: boolean
  error: string | null
  onRetry: () => void
}) {
  return (
    <Card className="p-lg">
      <div className="flex items-center justify-between gap-sm">
        <h2 className="text-lg font-bold text-ink">입찰 기록</h2>
        <span className="text-sm text-muted">전체 {bidCount.toLocaleString('ko-KR')}회</span>
      </div>

      {sealedBidActive && (
        <p className="mt-sm rounded-md bg-primary-tint p-sm text-xs text-primary">
          밀봉 입찰 내역은 경매 마감 후 한 번에 공개됩니다.
        </p>
      )}

      {error ? (
        <div className="flex flex-col items-center gap-sm py-xl text-center">
          <p className="text-sm text-muted">{error}</p>
          <Button variant="secondary" onClick={onRetry}>다시 시도</Button>
        </div>
      ) : bidLog.length === 0 ? (
        <EmptyState message="아직 공개된 입찰 기록이 없어요." />
      ) : (
        <ul
          aria-live="polite"
          className="mt-base flex max-h-[360px] flex-col divide-y divide-hairline-soft overflow-y-auto"
        >
          {bidLog.map((bid) => {
            const isMine = ownBidEntryIds.has(bid.entryId)
            return (
              <li
                key={bid.entryId}
                className="grid grid-cols-[auto_1fr_auto] items-center gap-base py-sm text-sm"
              >
                <span className="text-muted">
                  {formatTimeOfDay(new Date(bid.biddedAt))}
                </span>
                <span className={isMine ? 'font-semibold text-primary' : 'text-body'}>
                  {isMine ? '나의 입찰' : bid.bidder}
                </span>
                <span className={`font-semibold ${
                  isMine ? 'text-primary' : 'text-ink'
                }`}>
                  {formatWon(bid.amount)}
                </span>
              </li>
            )
          })}
        </ul>
      )}
    </Card>
  )
}

function PriceDropTimeline({
  auction,
  serverOffsetMs,
}: {
  auction: DownAuctionDetail
  serverOffsetMs: number
}) {
  // 진행 중에는 매 tick마다 새 하락 내역이 즉시 추가되도록 재렌더링한다.
  useDownAuctionClock(auction, serverOffsetMs)
  const completedAt = auction.finalPrice === null
    ? null
    : auction.startedAt
      + Math.ceil((auction.startPrice - auction.finalPrice) / auction.dropPrice)
      * auction.priceDropIntervalMs
  const effectiveNow = auction.status === 'COMPLETED' && completedAt !== null
    ? completedAt
    : auction.status === 'UNSOLD'
      ? auction.deadline
      : Math.min(Date.now() + serverOffsetMs, auction.deadline)
  const history = computeDropHistory(auction, effectiveNow).reverse()

  return (
    <Card className="p-lg">
      <h2 className="text-lg font-bold text-ink">전체 가격 변동 내역</h2>

      {history.length === 0 ? (
        <EmptyState message="아직 가격이 내려간 적이 없어요." />
      ) : (
        <ul className="mt-base flex flex-col divide-y divide-hairline-soft">
          {history.map((entry) => (
            <li
              key={`${entry.droppedAt}-${entry.price}`}
              className="flex items-center justify-between px-xs py-sm text-sm"
            >
              <span className="text-muted">
                {formatTimeOfDay(new Date(entry.droppedAt))}
              </span>
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

export default AuctionDetailPage
