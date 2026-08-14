import { Clock, Gavel, PackageCheck, ShieldCheck, Store, Trophy } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import Badge from '../../../components/ui/Badge'
import type { BadgeTone } from '../../../components/ui/Badge'
import Button from '../../../components/ui/Button'
import Card from '../../../components/ui/Card'
import Pagination from '../../auctions/components/Pagination'
import { useCountdown } from '../../../hooks/useCountdown'
import {
  requestMyBidRecords,
  requestMyDepositRecords,
  requestMyPage,
  requestMySaleRecords,
  requestMyTradeRecords,
} from '../../../lib/api/mypage'
import type {
  DepositStatus,
  HistorySort,
  MyBidRecord,
  MyDepositRecord,
  MySaleRecord,
  MyTradeRecord,
  PageResponse,
  TradeRoute,
  TradeStatus,
} from '../../../lib/api/mypage'
import { formatClock, formatTimeOfDay, formatWon } from '../../../lib/format'
import ItemThumbnail from '../components/ItemThumbnail'
import { DEFAULT_HISTORY_TAB, HISTORY_TAB, HISTORY_TAB_PARAM } from '../constants'
import type { HistoryTabKey } from '../constants'

const PAGE_SIZE = 10
const FIRST_PAGE = 1
const THUMBNAIL_CLASS = 'h-20 w-20'
const HISTORY_SKELETON_KEYS = Array.from({ length: 6 }, (_, index) => index)

const RECORD_TABS = [
  { key: HISTORY_TAB.bidding, label: '입찰 내역', icon: Gavel },
  { key: HISTORY_TAB.won, label: '낙찰 내역', icon: Trophy },
  { key: HISTORY_TAB.purchase, label: '구매 내역', icon: PackageCheck },
  { key: HISTORY_TAB.selling, label: '판매 내역', icon: Store },
  { key: HISTORY_TAB.deposit, label: '보증금 내역', icon: ShieldCheck },
] as const

const SORT_OPTIONS: { key: HistorySort; label: string }[] = [
  { key: 'latest', label: '최신순' },
  { key: 'oldest', label: '오래된순' },
]

interface FilterOption {
  label: string
  value: string
}

const ALL_FILTER: FilterOption = { label: '전체', value: '' }
const STATUS_FILTERS: Record<HistoryTabKey, FilterOption[]> = {
  bidding: [
    ALL_FILTER,
    { label: '최고가 입찰 중', value: 'WINNING' },
    { label: '다른 사람이 더 높음', value: 'LOSING' },
  ],
  won: [
    ALL_FILTER,
    { label: '결제 대기', value: 'WAITING_CONFIRM' },
    { label: '거래 중', value: 'CONFIRMED' },
    { label: '거래 완료', value: 'COMPLETED' },
    { label: '구매자 미확정', value: 'BUYER_FAILED' },
    { label: '판매자 미확정', value: 'SELLER_FAILED' },
  ],
  purchase: [ALL_FILTER],
  selling: [
    ALL_FILTER,
    { label: '진행중', value: 'ON_SALE' },
    { label: '낙찰 완료', value: 'SOLD' },
    { label: '유찰', value: 'FAILED' },
  ],
  deposit: [
    ALL_FILTER,
    { label: '보유중', value: 'HELD' },
    { label: '환불 완료', value: 'REFUNDED' },
    { label: '몰수', value: 'FORFEITED' },
    { label: '사용 완료', value: 'USED' },
  ],
}

const TRADE_STATUS_LABEL: Record<TradeStatus, string> = {
  WAITING_CONFIRM: '결제 대기',
  CONFIRMED: '거래 중',
  COMPLETED: '거래 완료',
  BUYER_FAILED: '구매자 미확정',
  SELLER_FAILED: '판매자 미확정',
}

const TRADE_STATUS_TONE: Record<TradeStatus, BadgeTone> = {
  WAITING_CONFIRM: 'primary',
  CONFIRMED: 'neutral',
  COMPLETED: 'success',
  BUYER_FAILED: 'danger',
  SELLER_FAILED: 'danger',
}

const TRADE_ROUTE_LABEL: Record<TradeRoute, string> = {
  WON: '낙찰',
  BUY_NOW: '즉시 구매',
}

const TRADE_ROUTE_TONE: Record<TradeRoute, BadgeTone> = {
  WON: 'neutral',
  BUY_NOW: 'primary',
}

const DEPOSIT_STATUS_LABEL: Record<DepositStatus, string> = {
  HELD: '보유중',
  REFUNDED: '환불 완료',
  FORFEITED: '몰수',
  USED: '사용 완료',
}

const DEPOSIT_STATUS_TONE: Record<DepositStatus, BadgeTone> = {
  HELD: 'primary',
  REFUNDED: 'success',
  FORFEITED: 'danger',
  USED: 'neutral',
}

type HistoryResult =
  | { kind: 'bidding'; data: PageResponse<MyBidRecord> }
  | { kind: 'won'; data: PageResponse<MyTradeRecord> }
  | { kind: 'purchase'; data: PageResponse<MyTradeRecord> }
  | { kind: 'selling'; data: PageResponse<MySaleRecord> }
  | { kind: 'deposit'; data: PageResponse<MyDepositRecord> }

interface HistorySummary {
  bidding: number
  won: number
  purchase: number
  selling: number
  deposit: number
  sellingOnSale: number
  heldDeposit: number
}

function isHistoryTabKey(value: string | null): value is HistoryTabKey {
  return RECORD_TABS.some((tab) => tab.key === value)
}

function formatRecordDate(timestamp: number) {
  const date = new Date(timestamp)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}.${day} ${formatTimeOfDay(date)}`
}

function MyRecordsPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [sort, setSort] = useState<HistorySort>('latest')
  const [selectedFilter, setSelectedFilter] = useState('')
  const [pagination, setPagination] = useState({ key: '', page: FIRST_PAGE })
  const [result, setResult] = useState<HistoryResult | null>(null)
  const [summary, setSummary] = useState<HistorySummary | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [retryToken, setRetryToken] = useState(0)

  const tabParam = searchParams.get(HISTORY_TAB_PARAM)
  const activeTab: HistoryTabKey = isHistoryTabKey(tabParam) ? tabParam : DEFAULT_HISTORY_TAB
  const availableFilters = STATUS_FILTERS[activeTab]
  const statusFilter = availableFilters.some((filter) => filter.value === selectedFilter)
    ? selectedFilter
    : ''
  const queryKey = `${activeTab}\u0000${statusFilter}\u0000${sort}`
  const page = pagination.key === queryKey ? pagination.page : FIRST_PAGE

  const redirectToLogin = useCallback(() => {
    const next = `${location.pathname}${location.search}`
    navigate(`/login?next=${encodeURIComponent(next)}`, { replace: true })
  }, [location.pathname, location.search, navigate])

  useEffect(() => {
    const controller = new AbortController()
    const summaryQuery = { page: FIRST_PAGE, size: 1, sort: 'latest' as const }

    Promise.all([
      requestMyBidRecords(summaryQuery, controller.signal),
      requestMyTradeRecords(summaryQuery, controller.signal),
      requestMyTradeRecords({ ...summaryQuery, status: 'COMPLETED' }, controller.signal),
      requestMySaleRecords(summaryQuery, controller.signal),
      requestMyDepositRecords(summaryQuery, controller.signal),
      requestMySaleRecords({ ...summaryQuery, status: 'ON_SALE' }, controller.signal),
      requestMyPage(controller.signal),
    ]).then(([bids, won, purchase, sales, deposits, onSale, myPage]) => {
      if (controller.signal.aborted) return
      const responses = [bids, won, purchase, sales, deposits, onSale, myPage]
      if (responses.some((response) => !response.ok && response.status === 401)) {
        redirectToLogin()
        return
      }
      if (
        bids.ok && won.ok && purchase.ok && sales.ok
        && deposits.ok && onSale.ok && myPage.ok
      ) {
        setSummary({
          bidding: bids.data.totalCount,
          won: won.data.totalCount,
          purchase: purchase.data.totalCount,
          selling: sales.data.totalCount,
          deposit: deposits.data.totalCount,
          sellingOnSale: onSale.data.totalCount,
          heldDeposit: myPage.data.deposit.inUse,
        })
      }
    })

    return () => controller.abort()
  }, [redirectToLogin, retryToken])

  useEffect(() => {
    const controller = new AbortController()
    const query = {
      page,
      size: PAGE_SIZE,
      sort,
      status: activeTab === 'purchase' ? 'COMPLETED' : statusFilter || undefined,
    }

    setIsLoading(true)
    setError(null)
    setResult(null)

    async function load() {
      if (activeTab === 'bidding') {
        const response = await requestMyBidRecords(query, controller.signal)
        return response.ok
          ? { ok: true as const, value: { kind: 'bidding' as const, data: response.data } }
          : response
      }
      if (activeTab === 'won' || activeTab === 'purchase') {
        const response = await requestMyTradeRecords(query, controller.signal)
        return response.ok
          ? { ok: true as const, value: { kind: activeTab, data: response.data } as HistoryResult }
          : response
      }
      if (activeTab === 'selling') {
        const response = await requestMySaleRecords(query, controller.signal)
        return response.ok
          ? { ok: true as const, value: { kind: 'selling' as const, data: response.data } }
          : response
      }
      const response = await requestMyDepositRecords(query, controller.signal)
      return response.ok
        ? { ok: true as const, value: { kind: 'deposit' as const, data: response.data } }
        : response
    }

    load().then((response) => {
      if (controller.signal.aborted) return
      setIsLoading(false)
      if (response.ok && 'value' in response) {
        setResult(response.value)
        return
      }
      if (!response.ok && response.status === 401) {
        redirectToLogin()
        return
      }
      if (!response.ok) setError(response.message)
    })

    return () => controller.abort()
  }, [activeTab, page, queryKey, redirectToLogin, retryToken, sort, statusFilter])

  function handleTabChange(tab: HistoryTabKey) {
    setSelectedFilter('')
    setSearchParams({ [HISTORY_TAB_PARAM]: tab })
  }

  const activeData = result?.kind === activeTab ? result.data : null
  const tabCounts: Record<HistoryTabKey, number | null> = {
    bidding: summary?.bidding ?? null,
    won: summary?.won ?? null,
    purchase: summary?.purchase ?? null,
    selling: summary?.selling ?? null,
    deposit: summary?.deposit ?? null,
  }

  return (
    <main className="mx-auto max-w-[1200px] px-lg py-xl">
      <h1 className="text-3xl font-bold text-ink">내 활동 기록</h1>
      <p className="mt-xs text-base text-body">
        입찰, 낙찰, 구매, 판매 및 보증금 내역을 한눈에 확인하세요.
      </p>

      <div className="mt-xl grid grid-cols-1 gap-xl lg:grid-cols-[280px_1fr]">
        <aside className="flex flex-col gap-base">
          <div className="grid grid-cols-2 gap-sm">
            {summary ? (
              <>
                <SummaryStat label="입찰 중" value={`${summary.bidding}건`} />
                <SummaryStat label="낙찰" value={`${summary.won}건`} />
                <SummaryStat label="판매 중" value={`${summary.sellingOnSale}건`} />
                <SummaryStat label="보유 보증금" value={formatWon(summary.heldDeposit)} />
              </>
            ) : (
              [0, 1, 2, 3].map((key) => <SummaryStatSkeleton key={key} />)
            )}
          </div>

          <nav className="flex flex-col gap-1 rounded-xl border border-hairline-soft bg-canvas p-sm">
            {RECORD_TABS.map((tab) => {
              const Icon = tab.icon
              const isActive = activeTab === tab.key
              return (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => handleTabChange(tab.key)}
                  className={`flex items-center gap-sm rounded-md px-base py-sm text-sm font-semibold transition-colors ${isActive ? 'bg-primary-tint text-primary' : 'text-body hover:bg-surface-soft'}`}
                >
                  <Icon size={16} />
                  <span className="flex-1 text-left">{tab.label}</span>
                  <span className={isActive ? 'text-primary' : 'text-muted'}>
                    {tabCounts[tab.key] ?? '—'}
                  </span>
                </button>
              )
            })}
          </nav>
        </aside>

        <section>
          <div className="flex flex-wrap items-center justify-between gap-sm">
            <div className="flex flex-wrap gap-xs">
              {availableFilters.map((filter) => (
                <button
                  key={filter.value || 'all'}
                  type="button"
                  onClick={() => setSelectedFilter(filter.value)}
                  className={`rounded-pill px-base py-1.5 text-xs font-semibold transition-colors ${statusFilter === filter.value ? 'bg-ink text-on-dark' : 'bg-surface-strong text-body hover:bg-hairline'}`}
                >
                  {filter.label}
                </button>
              ))}
            </div>

            <div className="flex gap-sm">
              {SORT_OPTIONS.map((option) => (
                <button
                  key={option.key}
                  type="button"
                  onClick={() => setSort(option.key)}
                  className={`text-xs font-semibold ${sort === option.key ? 'text-ink underline underline-offset-4' : 'text-muted'}`}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </div>

          <div className="mt-base grid grid-cols-1 gap-base md:grid-cols-2">
            {isLoading ? (
              <HistorySkeleton />
            ) : error ? (
              <div className="flex flex-col items-center gap-sm py-xl md:col-span-2">
                <p className="text-sm text-down">{error}</p>
                <Button variant="secondary" onClick={() => setRetryToken((value) => value + 1)}>
                  다시 시도
                </Button>
              </div>
            ) : (
              <HistoryCards result={result} activeTab={activeTab} />
            )}
          </div>

          {activeData && activeData.totalPages > 1 && (
            <div className="mt-lg border-t border-hairline-soft pt-base">
              <Pagination
                currentPage={activeData.page}
                totalPages={activeData.totalPages}
                onChange={(nextPage) => setPagination({ key: queryKey, page: nextPage })}
              />
            </div>
          )}
        </section>
      </div>
    </main>
  )
}

function HistoryCards({ result, activeTab }: { result: HistoryResult | null; activeTab: HistoryTabKey }) {
  if (!result || result.kind !== activeTab || result.data.items.length === 0) {
    return <EmptyState message="조건에 맞는 내역이 없어요." />
  }
  switch (result.kind) {
    case 'bidding':
      return result.data.items.map((record) => <BiddingCard key={record.auctionId} record={record} />)
    case 'won':
      return result.data.items.map((record) => <WonCard key={record.auctionId} record={record} />)
    case 'purchase':
      return result.data.items.map((record) => <PurchaseCard key={record.auctionId} record={record} />)
    case 'selling':
      return result.data.items.map((record) => <SellingCard key={record.auctionId} record={record} />)
    case 'deposit':
      return result.data.items.map((record) => <DepositRecordCard key={record.depositId} record={record} />)
  }
}

function SummaryStat({ label, value }: { label: string; value: string }) {
  return (
    <Card className="flex flex-col gap-1 p-base">
      <span className="text-xs text-muted">{label}</span>
      <span className="whitespace-nowrap text-lg font-bold text-ink">{value}</span>
    </Card>
  )
}

function SummaryStatSkeleton() {
  return (
    <Card aria-hidden className="flex animate-pulse flex-col gap-sm p-base">
      <div className="h-3 w-12 rounded-pill bg-surface-strong" />
      <div className="h-6 w-20 max-w-full rounded-pill bg-surface-strong" />
    </Card>
  )
}

function HistorySkeleton() {
  return (
    <div role="status" aria-label="내역을 불러오는 중" className="contents">
      <span className="sr-only">내역을 불러오는 중…</span>
      {HISTORY_SKELETON_KEYS.map((key) => (
        <Card key={key} aria-hidden className="flex h-28 animate-pulse gap-base p-base">
          <div className="h-20 w-20 shrink-0 rounded-lg bg-surface-strong" />
          <div className="flex flex-1 flex-col gap-sm py-xs">
            <div className="h-5 w-4/5 rounded-pill bg-surface-strong" />
            <div className="h-4 w-1/2 rounded-pill bg-surface-strong" />
            <div className="mt-auto h-5 w-2/3 rounded-pill bg-surface-strong" />
          </div>
        </Card>
      ))}
    </div>
  )
}

function EmptyState({ message }: { message: string }) {
  return <p className="py-xl text-center text-sm text-muted md:col-span-2">{message}</p>
}

function BiddingCard({ record }: { record: MyBidRecord }) {
  const { remaining, isUrgent } = useCountdown(record.deadline)
  return (
    <RecordLink auctionId={record.auctionId} thumbnailUrl={record.thumbnailUrl} title={record.title}>
      <p className="mt-1 text-sm text-muted">내 입찰가 {formatWon(record.myBidAmount)}</p>
      <div className="mt-auto flex items-center justify-between gap-sm pt-sm">
        <Badge tone={record.isWinning ? 'success' : 'danger'}>
          {record.isSealedPhase ? '밀봉입찰중' : record.isWinning ? '최고가 입찰 중' : '다른 사람이 더 높음'}
        </Badge>
        <span className={`flex shrink-0 items-center gap-1 text-xs font-semibold ${isUrgent ? 'text-down' : 'text-muted'}`}>
          <Clock size={12} />
          {formatClock(remaining)} 남음
        </span>
      </div>
    </RecordLink>
  )
}

function WonCard({ record }: { record: MyTradeRecord }) {
  return (
    <RecordLink auctionId={record.auctionId} thumbnailUrl={record.thumbnailUrl} title={record.title}>
      <p className="mt-1 text-sm text-muted">{formatRecordDate(record.purchasedAt)} 낙찰</p>
      <div className="mt-auto flex items-center justify-between gap-sm pt-sm">
        <Badge tone={TRADE_STATUS_TONE[record.status]}>{TRADE_STATUS_LABEL[record.status]}</Badge>
        <span className="text-base font-bold text-ink">{formatWon(record.finalPrice)}</span>
      </div>
    </RecordLink>
  )
}

function PurchaseCard({ record }: { record: MyTradeRecord }) {
  return (
    <RecordLink auctionId={record.auctionId} thumbnailUrl={record.thumbnailUrl} title={record.title}>
      <p className="mt-1 text-sm text-muted">{formatRecordDate(record.purchasedAt)} 구매</p>
      <div className="mt-auto flex items-center justify-between gap-sm pt-sm">
        <Badge tone={TRADE_ROUTE_TONE[record.route]}>{TRADE_ROUTE_LABEL[record.route]}</Badge>
        <span className="text-base font-bold text-ink">{formatWon(record.finalPrice)}</span>
      </div>
    </RecordLink>
  )
}

function SellingCard({ record }: { record: MySaleRecord }) {
  const status = record.status === 'COMPLETED'
    ? { label: '낙찰 완료', tone: 'success' as const }
    : record.status === 'UNSOLD'
      ? { label: '유찰', tone: 'muted' as const }
      : { label: '진행중', tone: 'dark' as const }
  return (
    <RecordLink auctionId={record.auctionId} thumbnailUrl={record.thumbnailUrl} title={record.title}>
      <p className="mt-1 text-sm text-muted">{formatRecordDate(record.listedAt)} 등록</p>
      <div className="mt-auto flex items-center justify-between gap-sm pt-sm">
        <Badge tone={status.tone}>{status.label}</Badge>
        <span className="text-base font-bold text-ink">{formatWon(record.price)}</span>
      </div>
    </RecordLink>
  )
}

function DepositRecordCard({ record }: { record: MyDepositRecord }) {
  return (
    <Card className="flex gap-base p-base">
      <span className="flex h-20 w-20 shrink-0 items-center justify-center rounded-lg bg-surface-soft text-muted">
        <ShieldCheck size={24} />
      </span>
      <div className="flex min-w-0 flex-1 flex-col justify-between gap-sm">
        <div>
          <p className="line-clamp-1 text-base font-semibold text-ink">{record.auctionTitle}</p>
          <p className="mt-1 text-sm text-muted">{formatRecordDate(record.changedAt)}</p>
        </div>
        <div className="flex items-center justify-between gap-sm">
          <Badge tone={DEPOSIT_STATUS_TONE[record.status]}>{DEPOSIT_STATUS_LABEL[record.status]}</Badge>
          <span className="text-base font-bold text-ink">{formatWon(record.amount)}</span>
        </div>
      </div>
    </Card>
  )
}

function RecordLink({
  auctionId,
  thumbnailUrl,
  title,
  children,
}: {
  auctionId: number
  thumbnailUrl: string | null
  title: string
  children: React.ReactNode
}) {
  return (
    <Link to={`/auctions/${auctionId}`}>
      <Card className="flex h-full gap-base p-base hover:shadow-soft">
        <ItemThumbnail thumbnailUrl={thumbnailUrl} className={THUMBNAIL_CLASS} />
        <div className="flex min-w-0 flex-1 flex-col">
          <p className="line-clamp-1 text-base font-semibold text-ink">{title}</p>
          {children}
        </div>
      </Card>
    </Link>
  )
}

export default MyRecordsPage
