import { Clock, Gavel, PackageCheck, ShieldCheck, Store } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import Badge from '../../../components/ui/Badge'
import type { BadgeTone } from '../../../components/ui/Badge'
import Card from '../../../components/ui/Card'
import { useCountdown } from '../../../hooks/useCountdown'
import { formatClock, formatTimeOfDay, formatWon } from '../../../lib/format'

/*
 * 마이페이지 내역 API가 아직 없어서 임시 데이터를 쓴다.
 * 실제 연동 시 아래 MOCK_* 배열을 fetch 결과로 바꾸면 된다.
 */
const RECORD_TABS = [
  { key: 'bidding', label: '입찰 중', icon: Gavel },
  { key: 'won', label: '낙찰/구매', icon: PackageCheck },
  { key: 'selling', label: '판매 내역', icon: Store },
  { key: 'deposit', label: '보증금 내역', icon: ShieldCheck },
] as const

type RecordTabKey = (typeof RECORD_TABS)[number]['key']

const SORT_OPTIONS = [
  { key: 'latest', label: '최신순' },
  { key: 'oldest', label: '오래된순' },
] as const

type SortKey = (typeof SORT_OPTIONS)[number]['key']

const ALL_FILTER = '전체'

const STATUS_FILTERS: Record<RecordTabKey, readonly string[]> = {
  bidding: [ALL_FILTER, '최고가 입찰 중', '다른 사람이 더 높음'],
  won: [ALL_FILTER, '결제 대기', '거래 중', '거래 완료'],
  selling: [ALL_FILTER, '진행중', '낙찰 완료', '유찰'],
  deposit: [ALL_FILTER, '보유중', '환불 완료', '몰수'],
}

interface BiddingRecord {
  auctionId: number
  title: string
  myBidAmount: number
  deadline: number
  isWinning: boolean
  biddedAt: number
}

interface WonRecord {
  auctionId: number
  title: string
  finalPrice: number
  wonAt: number
  tradeStatus: '결제 대기' | '거래 중' | '거래 완료'
}

interface SellingRecord {
  auctionId: number
  title: string
  price: number
  status: '진행중' | '낙찰 완료' | '유찰'
  listedAt: number
}

interface DepositRecord {
  depositId: number
  auctionTitle: string
  amount: number
  status: '보유중' | '환불 완료' | '몰수'
  changedAt: number
}

const MOCK_BIDDING: BiddingRecord[] = [
  { auctionId: 101, title: '소니 WH-1000XM5 노이즈캔슬링 헤드폰', myBidAmount: 220000, deadline: Date.now() + 6 * 60 * 1000, isWinning: true, biddedAt: Date.now() - 2 * 60 * 1000 },
  { auctionId: 102, title: '다이슨 에어랩 컴플리트', myBidAmount: 300000, deadline: Date.now() + 90 * 1000, isWinning: false, biddedAt: Date.now() - 10 * 60 * 1000 },
  { auctionId: 103, title: '아이패드 프로 11 (M4)', myBidAmount: 850000, deadline: Date.now() + 40 * 60 * 1000, isWinning: true, biddedAt: Date.now() - 60 * 60 * 1000 },
]

const MOCK_WON: WonRecord[] = [
  { auctionId: 90, title: '닌텐도 스위치 OLED', finalPrice: 265000, wonAt: Date.now() - 2 * 86400000, tradeStatus: '거래 완료' },
  { auctionId: 91, title: '캠핑 4인용 텐트 세트', finalPrice: 98000, wonAt: Date.now() - 1 * 86400000, tradeStatus: '거래 중' },
  { auctionId: 92, title: '르크루제 무쇠 냄비 세트', finalPrice: 150000, wonAt: Date.now() - 3 * 60 * 60 * 1000, tradeStatus: '결제 대기' },
]

const MOCK_SELLING: SellingRecord[] = [
  { auctionId: 70, title: '갤럭시 버즈2 프로', price: 95000, status: '진행중', listedAt: Date.now() - 1 * 86400000 },
  { auctionId: 71, title: '애플워치 SE', price: 180000, status: '낙찰 완료', listedAt: Date.now() - 5 * 86400000 },
  { auctionId: 72, title: '무선 청소기', price: 60000, status: '유찰', listedAt: Date.now() - 7 * 86400000 },
]

const MOCK_DEPOSIT: DepositRecord[] = [
  { depositId: 1, auctionTitle: '소니 WH-1000XM5 노이즈캔슬링 헤드폰', amount: 30000, status: '보유중', changedAt: Date.now() - 2 * 60 * 1000 },
  { depositId: 2, auctionTitle: '다이슨 에어랩 컴플리트', amount: 40000, status: '보유중', changedAt: Date.now() - 10 * 60 * 1000 },
  { depositId: 3, auctionTitle: '닌텐도 스위치 OLED', amount: 25000, status: '환불 완료', changedAt: Date.now() - 2 * 86400000 },
  { depositId: 4, auctionTitle: '캠핑 4인용 텐트 세트', amount: 15000, status: '몰수', changedAt: Date.now() - 1 * 86400000 },
]

const WON_STATUS_TONE: Record<WonRecord['tradeStatus'], BadgeTone> = {
  '결제 대기': 'primary',
  '거래 중': 'neutral',
  '거래 완료': 'success',
}

const SELLING_STATUS_TONE: Record<SellingRecord['status'], BadgeTone> = {
  진행중: 'dark',
  '낙찰 완료': 'success',
  유찰: 'muted',
}

const DEPOSIT_STATUS_TONE: Record<DepositRecord['status'], BadgeTone> = {
  보유중: 'primary',
  '환불 완료': 'success',
  몰수: 'danger',
}

function formatRecordDate(timestamp: number) {
  const date = new Date(timestamp)
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${m}.${d} ${formatTimeOfDay(date)}`
}

function sortByTime<T>(items: T[], sort: SortKey, getTime: (item: T) => number): T[] {
  return [...items].sort((a, b) =>
    sort === 'latest' ? getTime(b) - getTime(a) : getTime(a) - getTime(b),
  )
}

function biddingStatusLabel(record: BiddingRecord) {
  return record.isWinning ? '최고가 입찰 중' : '다른 사람이 더 높음'
}

function MyRecordsPage() {
  const [activeTab, setActiveTab] = useState<RecordTabKey>('bidding')
  const [sort, setSort] = useState<SortKey>('latest')
  const [statusFilter, setStatusFilter] = useState<string>(ALL_FILTER)

  function handleTabChange(tab: RecordTabKey) {
    setActiveTab(tab)
    setStatusFilter(ALL_FILTER)
  }

  const biddingRecords = sortByTime(MOCK_BIDDING, sort, (r) => r.biddedAt).filter(
    (r) => statusFilter === ALL_FILTER || biddingStatusLabel(r) === statusFilter,
  )
  const wonRecords = sortByTime(MOCK_WON, sort, (r) => r.wonAt).filter(
    (r) => statusFilter === ALL_FILTER || r.tradeStatus === statusFilter,
  )
  const sellingRecords = sortByTime(MOCK_SELLING, sort, (r) => r.listedAt).filter(
    (r) => statusFilter === ALL_FILTER || r.status === statusFilter,
  )
  const depositRecords = sortByTime(MOCK_DEPOSIT, sort, (r) => r.changedAt).filter(
    (r) => statusFilter === ALL_FILTER || r.status === statusFilter,
  )

  const heldDeposit = MOCK_DEPOSIT.filter((d) => d.status === '보유중').reduce(
    (sum, d) => sum + d.amount,
    0,
  )
  const sellingOnSaleCount = MOCK_SELLING.filter((r) => r.status === '진행중').length

  const tabCounts: Record<RecordTabKey, number> = {
    bidding: MOCK_BIDDING.length,
    won: MOCK_WON.length,
    selling: MOCK_SELLING.length,
    deposit: MOCK_DEPOSIT.length,
  }

  return (
    <main className="mx-auto max-w-[1200px] px-lg py-xl">
      <h1 className="text-3xl font-bold text-ink">내 활동 기록</h1>
      <p className="mt-xs text-base text-body">
        입찰·낙찰·판매·보증금 내역을 한눈에 확인하세요.
      </p>

      <div className="mt-xl grid grid-cols-1 gap-xl lg:grid-cols-[280px_1fr]">
        <aside className="flex flex-col gap-base">
          <div className="grid grid-cols-2 gap-sm">
            <SummaryStat label="입찰 중" value={`${MOCK_BIDDING.length}건`} />
            <SummaryStat label="낙찰/구매" value={`${MOCK_WON.length}건`} />
            <SummaryStat label="판매 중" value={`${sellingOnSaleCount}건`} />
            <SummaryStat label="보유 보증금" value={formatWon(heldDeposit)} />
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
                  className={`flex items-center gap-sm rounded-md px-base py-sm text-sm font-semibold transition-colors ${
                    isActive ? 'bg-primary-tint text-primary' : 'text-body hover:bg-surface-soft'
                  }`}
                >
                  <Icon size={16} />
                  <span className="flex-1 text-left">{tab.label}</span>
                  <span className={isActive ? 'text-primary' : 'text-muted'}>
                    {tabCounts[tab.key]}
                  </span>
                </button>
              )
            })}
          </nav>
        </aside>

        <section>
          <div className="flex flex-wrap items-center justify-between gap-sm">
            <div className="flex flex-wrap gap-xs">
              {STATUS_FILTERS[activeTab].map((filter) => (
                <button
                  key={filter}
                  type="button"
                  onClick={() => setStatusFilter(filter)}
                  className={`rounded-pill px-base py-1.5 text-xs font-semibold transition-colors ${
                    statusFilter === filter
                      ? 'bg-ink text-on-dark'
                      : 'bg-surface-strong text-body hover:bg-hairline'
                  }`}
                >
                  {filter}
                </button>
              ))}
            </div>

            <div className="flex gap-sm">
              {SORT_OPTIONS.map((option) => (
                <button
                  key={option.key}
                  type="button"
                  onClick={() => setSort(option.key)}
                  className={`text-xs font-semibold ${
                    sort === option.key ? 'text-ink underline underline-offset-4' : 'text-muted'
                  }`}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </div>

          <div className="mt-base grid grid-cols-1 gap-base md:grid-cols-2">
            {activeTab === 'bidding' &&
              (biddingRecords.length > 0 ? (
                biddingRecords.map((record) => (
                  <BiddingCard key={record.auctionId} record={record} />
                ))
              ) : (
                <EmptyState message="조건에 맞는 입찰 내역이 없어요." />
              ))}

            {activeTab === 'won' &&
              (wonRecords.length > 0 ? (
                wonRecords.map((record) => <WonCard key={record.auctionId} record={record} />)
              ) : (
                <EmptyState message="조건에 맞는 낙찰/구매 내역이 없어요." />
              ))}

            {activeTab === 'selling' &&
              (sellingRecords.length > 0 ? (
                sellingRecords.map((record) => (
                  <SellingCard key={record.auctionId} record={record} />
                ))
              ) : (
                <EmptyState message="조건에 맞는 판매 내역이 없어요." />
              ))}

            {activeTab === 'deposit' &&
              (depositRecords.length > 0 ? (
                depositRecords.map((record) => (
                  <DepositCard key={record.depositId} record={record} />
                ))
              ) : (
                <EmptyState message="조건에 맞는 보증금 내역이 없어요." />
              ))}
          </div>
        </section>
      </div>
    </main>
  )
}

function SummaryStat({ label, value }: { label: string; value: string }) {
  return (
    <Card className="flex flex-col gap-1 p-base">
      <span className="text-xs text-muted">{label}</span>
      <span className="text-lg font-bold text-ink">{value}</span>
    </Card>
  )
}

function EmptyState({ message }: { message: string }) {
  return <p className="py-xl text-center text-sm text-muted md:col-span-2">{message}</p>
}

function BiddingCard({ record }: { record: BiddingRecord }) {
  const { remaining, isUrgent } = useCountdown(record.deadline)

  return (
    <Link to={`/auctions/${record.auctionId}`}>
      <Card className="flex gap-base p-base hover:shadow-soft">
        <div className="h-20 w-20 shrink-0 rounded-lg bg-surface-soft" />
        <div className="flex min-w-0 flex-1 flex-col justify-between gap-sm">
          <div>
            <p className="line-clamp-1 text-base font-semibold text-ink">{record.title}</p>
            <p className="mt-1 text-sm text-muted">내 입찰가 {formatWon(record.myBidAmount)}</p>
          </div>
          <div className="flex items-center justify-between gap-sm">
            <Badge tone={record.isWinning ? 'success' : 'danger'}>
              {biddingStatusLabel(record)}
            </Badge>
            <span
              className={`flex shrink-0 items-center gap-1 text-xs font-semibold ${
                isUrgent ? 'text-down' : 'text-muted'
              }`}
            >
              <Clock size={12} />
              {formatClock(remaining)} 남음
            </span>
          </div>
        </div>
      </Card>
    </Link>
  )
}

function WonCard({ record }: { record: WonRecord }) {
  return (
    <Link to={`/auctions/${record.auctionId}`}>
      <Card className="flex gap-base p-base hover:shadow-soft">
        <div className="h-20 w-20 shrink-0 rounded-lg bg-surface-soft" />
        <div className="flex min-w-0 flex-1 flex-col justify-between gap-sm">
          <div>
            <p className="line-clamp-1 text-base font-semibold text-ink">{record.title}</p>
            <p className="mt-1 text-sm text-muted">{formatRecordDate(record.wonAt)} 낙찰</p>
          </div>
          <div className="flex items-center justify-between gap-sm">
            <Badge tone={WON_STATUS_TONE[record.tradeStatus]}>{record.tradeStatus}</Badge>
            <span className="text-base font-bold text-ink">{formatWon(record.finalPrice)}</span>
          </div>
        </div>
      </Card>
    </Link>
  )
}

function SellingCard({ record }: { record: SellingRecord }) {
  return (
    <Link to={`/auctions/${record.auctionId}`}>
      <Card className="flex gap-base p-base hover:shadow-soft">
        <div className="h-20 w-20 shrink-0 rounded-lg bg-surface-soft" />
        <div className="flex min-w-0 flex-1 flex-col justify-between gap-sm">
          <div>
            <p className="line-clamp-1 text-base font-semibold text-ink">{record.title}</p>
            <p className="mt-1 text-sm text-muted">{formatRecordDate(record.listedAt)} 등록</p>
          </div>
          <div className="flex items-center justify-between gap-sm">
            <Badge tone={SELLING_STATUS_TONE[record.status]}>{record.status}</Badge>
            <span className="text-base font-bold text-ink">{formatWon(record.price)}</span>
          </div>
        </div>
      </Card>
    </Link>
  )
}

function DepositCard({ record }: { record: DepositRecord }) {
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
          <Badge tone={DEPOSIT_STATUS_TONE[record.status]}>{record.status}</Badge>
          <span className="text-base font-bold text-ink">{formatWon(record.amount)}</span>
        </div>
      </div>
    </Card>
  )
}

export default MyRecordsPage
