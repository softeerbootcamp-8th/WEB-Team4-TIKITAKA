import { AlertCircle, ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { formatWon } from '../../../lib/format'
import {
  ACTIVE_TRADE_TEXT,
  HISTORY_TAB,
  TRADE_MY_TURN_ROLES,
  TRADE_NEXT_ACTION_LABEL,
  TRADE_ROLE_LABEL,
  historyPath,
} from '../constants'
import type { ActiveTrade } from '../types'
import ItemThumbnail from './ItemThumbnail'

/*
 * 아직 끝나지 않은 거래 알림. 완료되지 않은 거래가 하나도 없으면 아예 그리지 않는다.
 * 마이페이지에서 유일하게 배경색을 쓰는 자리라, 여기 색이 곧 "지금 할 일이 있다"는 신호다.
 */
const HEADING_ICON_SIZE = 18
const CHEVRON_SIZE = 14
const THUMBNAIL_CLASS = 'h-14 w-14'
/** 가로 스크롤 카드 한 장의 폭. 제목 두 줄이 들어가는 최소 폭으로 잡았다. */
const CARD_WIDTH_CLASS = 'w-[240px]'

function ActiveTradeBanner({ trades }: { trades: ActiveTrade[] }) {
  if (trades.length === 0) return null

  return (
    <section className="rounded-xl bg-down-tint p-lg">
      <div className="flex flex-col gap-base lg:flex-row lg:items-start lg:gap-lg">
        <div className="shrink-0 lg:w-[220px]">
          {/* break-keep: 한국어가 낱말 중간에서 끊기지 않게 한다("있어 / 요" 방지). */}
          <h2 className="flex items-start gap-xs break-keep text-lg font-bold leading-snug text-ink">
            <AlertCircle size={HEADING_ICON_SIZE} className="mt-1 shrink-0 text-down" />
            {ACTIVE_TRADE_TEXT.title(trades.length)}
          </h2>
          <p className="mt-xxs break-keep text-sm leading-relaxed text-body">
            {ACTIVE_TRADE_TEXT.description}
          </p>
          <Link
            to={historyPath(HISTORY_TAB.won)}
            className="mt-xs inline-flex items-center gap-0.5 text-sm font-semibold text-ink hover:underline"
          >
            {ACTIVE_TRADE_TEXT.viewAll}
            <ChevronRight size={CHEVRON_SIZE} />
          </Link>
        </div>

        {/* 카드가 넘치면 가로로 스크롤한다. 세로로 쌓으면 배너가 화면을 다 먹는다. */}
        <ul className="-mx-1 flex snap-x gap-sm overflow-x-auto px-1 pb-xs lg:flex-1">
          {trades.map((trade) => (
            <li key={trade.tradeId} className={`${CARD_WIDTH_CLASS} shrink-0 snap-start`}>
              <ActiveTradeCard trade={trade} />
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}

function ActiveTradeCard({ trade }: { trade: ActiveTrade }) {
  const isMyTurn = TRADE_MY_TURN_ROLES[trade.status].includes(trade.role)

  return (
    <Link
      to={`/trades/${trade.tradeId}`}
      className="flex h-full flex-col gap-sm rounded-lg bg-canvas p-sm transition-shadow hover:shadow-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
    >
      <div className="flex gap-sm">
        <ItemThumbnail thumbnailUrl={trade.thumbnailUrl} className={THUMBNAIL_CLASS} />
        <div className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="w-fit rounded-pill bg-surface-strong px-2 py-0.5 text-[11px] font-semibold text-body">
            {TRADE_ROLE_LABEL[trade.role]}
          </span>
          <p className="line-clamp-2 text-xs font-semibold leading-snug text-ink">
            {trade.title}
          </p>
          <p className="text-sm font-bold text-ink">{formatWon(trade.price)}</p>
        </div>
      </div>

      <div className="mt-auto border-t border-hairline-soft pt-xs">
        <span className={`text-xs font-semibold ${isMyTurn ? 'text-down' : 'text-muted'}`}>
          {TRADE_NEXT_ACTION_LABEL[trade.status][trade.role]}
        </span>
      </div>
    </Link>
  )
}

export default ActiveTradeBanner
