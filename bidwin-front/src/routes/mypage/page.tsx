import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import { useAuctionEvents } from '../../hooks/useAuctionEvents'
import { useServerClock } from '../../hooks/useServerClock'
import { requestMyPage } from '../../lib/api/mypage'
import type { MyPageResponse } from '../../lib/api/mypage'
import ActiveTradeBanner from './components/ActiveTradeBanner'
import DepositCard from './components/DepositCard'
import MyInfoDrawer from './components/MyInfoDrawer'
import MyItemSection from './components/MyItemSection'
import ProfileCard from './components/ProfileCard'
import SettingsSection from './components/SettingsSection'
import {
  BUYING_SECTION_TEXT,
  HISTORY_TAB,
  LEAVE_MODAL_TEXT,
  MYPAGE_TEXT,
  SELLING_SECTION_TEXT,
  historyPath,
} from './constants'
import { toBuyingCard, toSellingCard } from './view'

const CONTENT_WIDTH_CLASS = 'max-w-[960px]'
const AUCTION_LIST_PATH = '/auctions'
const AUCTION_NEW_PATH = '/auctions/new'

function MyPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [data, setData] = useState<MyPageResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [retryToken, setRetryToken] = useState(0)
  const [isMyInfoOpen, setIsMyInfoOpen] = useState(false)
  const initialServerTime = data?.sellingItems.find((item) => item.downPricing)
    ?.downPricing?.serverTime
  const { serverOffsetMs, synchronize } = useServerClock(initialServerTime)

  useEffect(() => {
    const controller = new AbortController()
    setIsLoading(true)
    setError(null)

    requestMyPage(controller.signal).then((result) => {
      if (controller.signal.aborted) return
      setIsLoading(false)
      if (result.ok) {
        setData(result.data)
        return
      }
      if (result.status === 401) {
        const next = `${location.pathname}${location.search}`
        navigate(`/login?next=${encodeURIComponent(next)}`, { replace: true })
        return
      }
      setError(result.message)
    })

    return () => controller.abort()
  }, [location.pathname, location.search, navigate, retryToken])

  useAuctionEvents(
    'list',
    data?.sellingItems.map((item) => item.auctionId) ?? [],
    {
      onHeartbeat: synchronize,
      onState: (state) => {
        setData((current) => {
          if (!current) return current
          const sellingItems = current.sellingItems.map((item) => {
            if (item.auctionId !== state.auctionId) return item
            return {
              ...item,
              price: state.currentPrice,
              status: state.status === 'COMPLETED'
                ? 'SOLD' as const
                : state.status === 'UNSOLD'
                  ? 'FAILED' as const
                  : 'ON_SALE' as const,
            }
          })
          return { ...current, sellingItems }
        })
      },
    },
  )

  if (isLoading) return <MyPageSkeleton />
  if (error || !data) {
    return (
      <PageMessage message={error ?? '마이페이지를 불러오지 못했습니다.'}>
        <Button variant="secondary" onClick={() => setRetryToken((value) => value + 1)}>
          다시 시도
        </Button>
      </PageMessage>
    )
  }

  const leaveBlockReason = data.activeTrades.length > 0
    ? LEAVE_MODAL_TEXT.blockedByTrade(data.activeTrades.length)
    : data.deposit.inUse > 0
      ? LEAVE_MODAL_TEXT.blockedByDeposit
      : LEAVE_MODAL_TEXT.unavailable

  return (
    <main className={`mx-auto flex ${CONTENT_WIDTH_CLASS} flex-col gap-lg px-lg py-xl`}>
      <h1 className="text-2xl font-bold text-ink">{MYPAGE_TEXT.title}</h1>

      <ActiveTradeBanner trades={data.activeTrades} />
      <ProfileCard profile={data.profile} onManage={() => setIsMyInfoOpen(true)} />
      <DepositCard deposit={data.deposit} />

      <MyItemSection
        title={SELLING_SECTION_TEXT.title}
        items={data.sellingItems.map(toSellingCard)}
        viewAllLabel={SELLING_SECTION_TEXT.viewAll}
        viewAllPath={historyPath(HISTORY_TAB.selling)}
        emptyMessage={SELLING_SECTION_TEXT.empty}
        emptyActionLabel={SELLING_SECTION_TEXT.emptyAction}
        emptyActionPath={AUCTION_NEW_PATH}
        serverOffsetMs={serverOffsetMs}
      />

      <MyItemSection
        title={BUYING_SECTION_TEXT.title}
        items={data.buyingItems.map(toBuyingCard)}
        viewAllLabel={BUYING_SECTION_TEXT.viewAll}
        viewAllPath={historyPath(HISTORY_TAB.purchase)}
        emptyMessage={BUYING_SECTION_TEXT.empty}
        emptyActionLabel={BUYING_SECTION_TEXT.emptyAction}
        emptyActionPath={AUCTION_LIST_PATH}
        serverOffsetMs={serverOffsetMs}
      />

      <SettingsSection onOpenMyInfo={() => setIsMyInfoOpen(true)} />

      <MyInfoDrawer
        isOpen={isMyInfoOpen}
        onClose={() => setIsMyInfoOpen(false)}
        profile={data.profile}
        onChangeNickname={(nickname) => {
          setData((current) => current
            ? { ...current, profile: { ...current.profile, nickname } }
            : current)
        }}
        onChangeImage={(profileImageUrl) => {
          setData((current) => current
            ? { ...current, profile: { ...current.profile, profileImageUrl } }
            : current)
        }}
        leaveBlockReason={leaveBlockReason}
      />
    </main>
  )
}

function PageMessage({ message, children }: { message: string; children?: React.ReactNode }) {
  return (
    <main className="flex min-h-[calc(100dvh-4rem)] flex-col items-center justify-center gap-base px-lg text-center">
      <p className="text-base text-body">{message}</p>
      {children}
    </main>
  )
}

function MyPageSkeleton() {
  return (
    <main
      role="status"
      aria-label="마이페이지를 불러오는 중"
      className={`mx-auto flex ${CONTENT_WIDTH_CLASS} flex-col gap-lg px-lg py-xl`}
    >
      <span className="sr-only">마이페이지를 불러오는 중…</span>
      <div className="h-8 w-28 rounded-pill bg-surface-strong motion-safe:animate-pulse" />

      <Card className="flex items-center gap-lg motion-safe:animate-pulse">
        <div className="h-20 w-20 shrink-0 rounded-full bg-surface-strong" />
        <div className="flex flex-1 flex-col gap-sm">
          <div className="h-6 w-36 rounded-pill bg-surface-strong" />
          <div className="h-4 w-64 max-w-full rounded-pill bg-surface-strong" />
        </div>
        <div className="hidden h-10 w-28 rounded-pill bg-surface-strong sm:block" />
      </Card>

      <Card className="motion-safe:animate-pulse">
        <div className="h-5 w-24 rounded-pill bg-surface-strong" />
        <div className="mt-base h-8 w-52 max-w-full rounded-pill bg-surface-strong" />
        <div className="mt-base grid grid-cols-2 gap-sm">
          <div className="h-16 rounded-lg bg-surface-strong" />
          <div className="h-16 rounded-lg bg-surface-strong" />
        </div>
      </Card>

      {[0, 1].map((sectionKey) => (
        <section key={sectionKey} className="rounded-xl border border-hairline-soft bg-canvas p-lg motion-safe:animate-pulse">
          <div className="h-5 w-32 rounded-pill bg-surface-strong" />
          <div className="mt-sm grid grid-cols-1 gap-sm sm:grid-cols-2 lg:grid-cols-3">
            {[0, 1, 2].map((itemKey) => (
              <div key={itemKey} className="flex h-28 gap-sm rounded-lg bg-surface-soft p-sm">
                <div className="h-16 w-16 shrink-0 rounded-md bg-surface-strong" />
                <div className="flex flex-1 flex-col gap-sm py-xs">
                  <div className="h-4 w-full rounded-pill bg-surface-strong" />
                  <div className="h-4 w-3/4 rounded-pill bg-surface-strong" />
                </div>
              </div>
            ))}
          </div>
        </section>
      ))}
    </main>
  )
}

export default MyPage
