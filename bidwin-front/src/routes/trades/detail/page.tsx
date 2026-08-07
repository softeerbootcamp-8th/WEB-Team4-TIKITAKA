import { ArrowLeft, Ban, CheckCircle2, Clock, ImageIcon, Phone } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import Badge from '../../../components/ui/Badge'
import Button from '../../../components/ui/Button'
import Card from '../../../components/ui/Card'
import { useAuth } from '../../../hooks/useAuth'
import { useToast } from '../../../hooks/useToast'
import { useTradeEvents } from '../../../hooks/useTradeEvents'
import type { TradeLiveState } from '../../../hooks/useTradeEvents'
import type { ApiResult } from '../../../lib/api/client'
import type { TradeStatus } from '../../../lib/api/mypage'
import {
  requestBuyerConfirmation,
  requestSellerConfirmation,
  requestTradeDetail,
} from '../../../lib/api/trades'
import type { TradeDetail } from '../../../lib/api/trades'
import { formatWon } from '../../../lib/format'
import {
  MYPAGE_PATH,
  TRADE_DETAIL_TEXT,
  TRADE_STATUS_LABEL,
  TRADE_STATUS_TONE,
} from './constants'

type LoadPhase = 'loading' | 'ready' | 'notFound' | 'denied' | 'error'

const CONTENT_WIDTH_CLASS = 'max-w-[640px]'
const THUMBNAIL_CLASS = 'h-20 w-20'
const ICON_SIZE = 18
const TERMINAL_ICON_SIZE = 30

/** 아직 진행 중이라 실시간 갱신이 필요한 상태. 종료 상태에는 SSE를 열지 않는다. */
const LIVE_STATUSES: readonly TradeStatus[] = ['WAITING_CONFIRM', 'CONFIRMED']

function TradeDetailPage() {
  const { tradeId: rawTradeId } = useParams()
  const tradeId = Number(rawTradeId)
  const isValidTradeId = Number.isSafeInteger(tradeId) && tradeId > 0

  const navigate = useNavigate()
  const location = useLocation()
  const { setAuthenticated } = useAuth()
  const { showToast } = useToast()

  const [trade, setTrade] = useState<TradeDetail | null>(null)
  const [phase, setPhase] = useState<LoadPhase>(isValidTradeId ? 'loading' : 'notFound')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isPending, setIsPending] = useState(false)
  const [retryToken, setRetryToken] = useState(0)

  const redirectToLogin = useCallback(() => {
    setAuthenticated(false)
    const next = `${location.pathname}${location.search}`
    navigate(`/login?next=${encodeURIComponent(next)}`, { replace: true })
  }, [location.pathname, location.search, navigate, setAuthenticated])

  const applyFailure = useCallback(
    (result: Extract<ApiResult<unknown>, { ok: false }>) => {
      if (result.status === 401) {
        redirectToLogin()
        return
      }
      if (result.status === 403) {
        setPhase('denied')
        return
      }
      if (result.status === 404) {
        setPhase('notFound')
        return
      }
      setErrorMessage(result.message)
      setPhase('error')
    },
    [redirectToLogin],
  )

  // 초기 로드와 수동 재시도. 성공하면 상세를 저장하고, 실패는 상태 코드별로 분기한다.
  useEffect(() => {
    if (!isValidTradeId) return
    const controller = new AbortController()
    setPhase('loading')
    setErrorMessage(null)
    requestTradeDetail(tradeId, controller.signal).then((result) => {
      if (controller.signal.aborted) return
      if (result.ok) {
        setTrade(result.data)
        setPhase('ready')
        return
      }
      applyFailure(result)
    })
    return () => controller.abort()
  }, [tradeId, isValidTradeId, retryToken, applyFailure])

  // SSE는 상태 변경만 알린다. 연락처는 서버가 게이팅하므로 상세를 조용히 다시 읽어 반영한다.
  const statusRef = useRef<TradeStatus | null>(null)
  statusRef.current = trade?.status ?? null

  const refresh = useCallback(async () => {
    const result = await requestTradeDetail(tradeId)
    if (result.ok) {
      setTrade(result.data)
      setPhase('ready')
      return
    }
    applyFailure(result)
  }, [tradeId, applyFailure])

  const handleLiveState = useCallback(
    (state: TradeLiveState) => {
      if (statusRef.current !== null && state.status !== statusRef.current) {
        void refresh()
      }
    },
    [refresh],
  )

  const isLive = trade !== null && LIVE_STATUSES.includes(trade.status)
  useTradeEvents(isLive ? tradeId : null, handleLiveState)

  const goToMyPage = useCallback(() => navigate(MYPAGE_PATH), [navigate])

  const runConfirm = useCallback(
    async (action: () => Promise<ApiResult<unknown>>, successMessage: string) => {
      if (isPending) return
      setIsPending(true)
      try {
        const result = await action()
        if (result.ok) {
          showToast(successMessage, 'success')
          await refresh()
          return
        }
        if (result.status === 401) {
          redirectToLogin()
          return
        }
        // 실패해도 최신 상태로 맞춘다(상대가 먼저 확정해 상태가 이미 바뀐 경우 등).
        showToast(result.message, 'info')
        await refresh()
      } finally {
        // 상세 재조회까지 버튼을 잠가 같은 상태로 확정 요청이 중복 전송되지 않게 한다.
        setIsPending(false)
      }
    },
    [isPending, refresh, redirectToLogin, showToast],
  )

  if (!isValidTradeId || phase === 'notFound') {
    return (
      <TradeMessage
        title={TRADE_DETAIL_TEXT.notFoundTitle}
        description={TRADE_DETAIL_TEXT.notFoundDescription}
        onBack={goToMyPage}
      />
    )
  }
  if (phase === 'denied') {
    return (
      <TradeMessage
        title={TRADE_DETAIL_TEXT.accessDeniedTitle}
        description={TRADE_DETAIL_TEXT.accessDeniedDescription}
        onBack={goToMyPage}
      />
    )
  }
  if (phase === 'error') {
    return (
      <TradeMessage
        title={TRADE_DETAIL_TEXT.errorTitle}
        description={errorMessage ?? TRADE_DETAIL_TEXT.errorDescription}
        onRetry={() => setRetryToken((value) => value + 1)}
        onBack={goToMyPage}
      />
    )
  }
  if (phase === 'loading' || trade === null) {
    return <TradeMessage title={TRADE_DETAIL_TEXT.loading} />
  }

  return (
    <main className={`mx-auto flex ${CONTENT_WIDTH_CLASS} flex-col gap-lg px-lg py-xl`}>
      <button
        type="button"
        onClick={goToMyPage}
        className="inline-flex w-fit items-center gap-1 text-sm font-semibold text-muted transition-colors hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-canvas"
      >
        <ArrowLeft size={ICON_SIZE} />
        {TRADE_DETAIL_TEXT.back}
      </button>

      <TradeSummary trade={trade} />

      <ActionPanel
        trade={trade}
        isPending={isPending}
        onBuyerConfirm={() =>
          runConfirm(
            () => requestBuyerConfirmation(trade.tradeId),
            TRADE_DETAIL_TEXT.purchaseConfirmed,
          )
        }
        onSellerConfirm={() =>
          runConfirm(
            () => requestSellerConfirmation(trade.tradeId),
            TRADE_DETAIL_TEXT.saleConfirmed,
          )
        }
        onBack={goToMyPage}
      />
    </main>
  )
}

function TradeSummary({ trade }: { trade: TradeDetail }) {
  return (
    <Card className="flex items-center gap-base">
      <TradeThumbnail thumbnailUrl={trade.thumbnailUrl} />
      <div className="flex min-w-0 flex-1 flex-col gap-xxs">
        <Badge tone={TRADE_STATUS_TONE[trade.status]}>{TRADE_STATUS_LABEL[trade.status]}</Badge>
        <p className="line-clamp-2 text-base font-semibold leading-snug text-ink">{trade.title}</p>
        <p className="text-sm text-muted">
          {TRADE_DETAIL_TEXT.priceLabel}{' '}
          <span className="font-bold text-ink">{formatWon(trade.finalPrice)}</span>
        </p>
      </div>
    </Card>
  )
}

function TradeThumbnail({ thumbnailUrl }: { thumbnailUrl: string | null }) {
  if (thumbnailUrl) {
    return (
      <img
        src={thumbnailUrl}
        alt=""
        className={`${THUMBNAIL_CLASS} shrink-0 rounded-lg object-cover`}
      />
    )
  }
  return (
    <span
      className={`${THUMBNAIL_CLASS} flex shrink-0 items-center justify-center rounded-lg bg-surface-strong text-muted-soft`}
    >
      <ImageIcon size={ICON_SIZE} />
    </span>
  )
}

interface ActionPanelProps {
  trade: TradeDetail
  isPending: boolean
  onBuyerConfirm: () => void
  onSellerConfirm: () => void
  onBack: () => void
}

/*
 * 상태 × 역할에 따라 화면이 갈린다.
 * WAITING_CONFIRM: 구매자만 구매확정, 판매자는 대기.
 * CONFIRMED: 구매자는 판매자 연락처 확인 + 대기, 판매자만 판매확정.
 * COMPLETED/종료: 진행 UI 없이 안내와 이동 버튼만 보여준다.
 */
function ActionPanel({
  trade,
  isPending,
  onBuyerConfirm,
  onSellerConfirm,
  onBack,
}: ActionPanelProps) {
  const isBuyer = trade.role === 'BUYER'

  if (trade.status === 'WAITING_CONFIRM') {
    if (isBuyer) {
      return (
        <ActionCard
          title={TRADE_DETAIL_TEXT.buyerActionTitle}
          description={TRADE_DETAIL_TEXT.buyerActionDescription}
        >
          <Button size="lg" className="w-full" onClick={onBuyerConfirm} disabled={isPending}>
            {TRADE_DETAIL_TEXT.confirmPurchase}
          </Button>
        </ActionCard>
      )
    }
    return (
      <WaitingCard
        title={TRADE_DETAIL_TEXT.waitingBuyerTitle}
        description={TRADE_DETAIL_TEXT.waitingBuyerDescription}
      />
    )
  }

  if (trade.status === 'CONFIRMED') {
    if (isBuyer) {
      return (
        <div className="flex flex-col gap-base">
          <ContactCard contact={trade.sellerContact} />
          <WaitingCard
            title={TRADE_DETAIL_TEXT.buyerWaitingSellerTitle}
            description={TRADE_DETAIL_TEXT.buyerWaitingSellerDescription}
          />
        </div>
      )
    }
    return (
      <ActionCard
        title={TRADE_DETAIL_TEXT.sellerActionTitle}
        description={TRADE_DETAIL_TEXT.sellerActionDescription}
      >
        <Button size="lg" className="w-full" onClick={onSellerConfirm} disabled={isPending}>
          {TRADE_DETAIL_TEXT.confirmSale}
        </Button>
      </ActionCard>
    )
  }

  if (trade.status === 'COMPLETED') {
    return (
      <TerminalCard
        tone="success"
        title={TRADE_DETAIL_TEXT.completedTitle}
        description={TRADE_DETAIL_TEXT.completedDescription}
        onBack={onBack}
      />
    )
  }

  return (
    <TerminalCard
      tone="muted"
      title={TRADE_DETAIL_TEXT.endedTitle}
      description={TRADE_DETAIL_TEXT.endedDescription}
      onBack={onBack}
    />
  )
}

function ActionCard({
  title,
  description,
  children,
}: {
  title: string
  description: string
  children: ReactNode
}) {
  return (
    <Card className="flex flex-col gap-base">
      <div className="flex flex-col gap-xxs">
        <h2 className="text-lg font-bold text-ink">{title}</h2>
        <p className="text-sm leading-relaxed text-body">{description}</p>
      </div>
      {children}
    </Card>
  )
}

function WaitingCard({ title, description }: { title: string; description: string }) {
  return (
    <Card className="flex items-start gap-base">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-surface-strong text-muted">
        <Clock size={ICON_SIZE} />
      </span>
      <div className="flex flex-col gap-xxs">
        <h2 className="text-base font-bold text-ink">{title}</h2>
        <p className="text-sm leading-relaxed text-body">{description}</p>
      </div>
    </Card>
  )
}

function ContactCard({ contact }: { contact: string | null }) {
  return (
    <div className="flex flex-col gap-sm rounded-xl border border-hairline-soft bg-primary-tint p-xl">
      <div className="flex items-center gap-xs text-primary">
        <Phone size={ICON_SIZE} />
        <h2 className="text-base font-bold">{TRADE_DETAIL_TEXT.contactTitle}</h2>
      </div>
      {contact ? (
        <p className="select-all break-all text-xl font-bold text-ink">{contact}</p>
      ) : (
        <p className="text-sm text-body">{TRADE_DETAIL_TEXT.contactUnavailable}</p>
      )}
      <p className="text-sm leading-relaxed text-body">{TRADE_DETAIL_TEXT.contactDescription}</p>
    </div>
  )
}

function TerminalCard({
  tone,
  title,
  description,
  onBack,
}: {
  tone: 'success' | 'muted'
  title: string
  description: string
  onBack: () => void
}) {
  const Icon = tone === 'success' ? CheckCircle2 : Ban
  const iconClass =
    tone === 'success' ? 'bg-up-tint text-up' : 'bg-surface-strong text-muted'

  return (
    <Card className="flex flex-col items-center gap-base py-xl text-center">
      <span className={`flex h-14 w-14 items-center justify-center rounded-full ${iconClass}`}>
        <Icon size={TERMINAL_ICON_SIZE} />
      </span>
      <div className="flex flex-col gap-xxs">
        <h2 className="text-lg font-bold text-ink">{title}</h2>
        <p className="text-sm leading-relaxed text-body">{description}</p>
      </div>
      <Button variant="secondary" size="lg" onClick={onBack}>
        {TRADE_DETAIL_TEXT.back}
      </Button>
    </Card>
  )
}

function TradeMessage({
  title,
  description,
  onRetry,
  onBack,
}: {
  title: string
  description?: string
  onRetry?: () => void
  onBack?: () => void
}) {
  return (
    <main className="mx-auto flex min-h-[calc(100dvh-4rem)] max-w-[640px] flex-col items-center justify-center gap-base px-lg text-center">
      <h1 className="text-lg font-bold text-ink">{title}</h1>
      {description && <p className="text-sm leading-relaxed text-body">{description}</p>}
      {(onRetry || onBack) && (
        <div className="flex gap-sm">
          {onRetry && (
            <Button variant="secondary" onClick={onRetry}>
              {TRADE_DETAIL_TEXT.retry}
            </Button>
          )}
          {onBack && (
            <Button variant={onRetry ? 'tertiary' : 'secondary'} onClick={onBack}>
              {TRADE_DETAIL_TEXT.back}
            </Button>
          )}
        </div>
      )}
    </main>
  )
}

export default TradeDetailPage
