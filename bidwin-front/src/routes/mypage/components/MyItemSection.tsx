import { ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import Badge from '../../../components/ui/Badge'
import RollingPrice from '../../../components/ui/RollingPrice'
import { formatWon } from '../../../lib/format'
import { useDownAuctionClock } from '../../../hooks/useDownAuctionClock'
import type { DownPricing } from '../../../lib/auctionPricing'
import { ITEM_CARD_TEXT, ITEM_PREVIEW_LIMIT } from '../constants'
import { ongoingFirst } from '../view'
import type { ItemCardModel } from '../view'
import ItemThumbnail from './ItemThumbnail'

/*
 * 판매 물품·구매 물품 섹션. 둘은 상태 문구만 다르고 구조가 같아서 한 컴포넌트로 쓴다.
 * 여기서는 미리보기만 하고, 관리·수정은 하지 않는다. 오른쪽 화살표가 전체 내역으로 넘긴다.
 */
const CHEVRON_SIZE = 18
const THUMBNAIL_CLASS = 'h-16 w-16'
/* 프로필·보증금·설정과 같은 흰 카드 안에, 물품은 회색 타일로 한 단계 안쪽에 놓는다. */
const SECTION_SURFACE_CLASS = 'rounded-xl border border-hairline-soft bg-canvas p-lg'
const TILE_SURFACE_CLASS = 'rounded-lg bg-surface-soft'

function MyItemSection({
  title,
  items,
  viewAllLabel,
  viewAllPath,
  emptyMessage,
  emptyActionLabel,
  emptyActionPath,
  serverOffsetMs,
}: {
  title: string
  items: ItemCardModel[]
  viewAllLabel: string
  viewAllPath: string
  emptyMessage: string
  emptyActionLabel: string
  emptyActionPath: string
  serverOffsetMs: number
}) {
  /* 미리보기 자리는 몇 개뿐이라, 아직 진행 중인 물품이 먼저 보이게 한다. */
  const visibleItems = ongoingFirst(items).slice(0, ITEM_PREVIEW_LIMIT)

  return (
    <section className={SECTION_SURFACE_CLASS}>
      <div className="flex items-center justify-between gap-base">
        <h2 className="text-base font-bold text-ink">{title}</h2>

        <Link
          to={viewAllPath}
          aria-label={viewAllLabel}
          className="inline-flex shrink-0 items-center gap-0.5 rounded-pill text-sm font-semibold text-body hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          {/* 좁은 화면에서는 화살표만 남긴다. 문구는 aria-label로 계속 읽힌다. */}
          <span className="hidden sm:inline">{viewAllLabel}</span>
          <ChevronRight size={CHEVRON_SIZE} />
        </Link>
      </div>

      {visibleItems.length > 0 ? (
        <ul className="mt-sm grid grid-cols-1 gap-sm sm:grid-cols-2 lg:grid-cols-3">
          {visibleItems.map((item) => (
            <li key={item.auctionId}>
              <MyItemCard item={item} serverOffsetMs={serverOffsetMs} />
            </li>
          ))}
        </ul>
      ) : (
        <div
          className={`mt-sm flex flex-col items-center gap-xs px-lg py-xl ${TILE_SURFACE_CLASS}`}
        >
          <p className="text-sm text-muted">{emptyMessage}</p>
          <Link
            to={emptyActionPath}
            className="text-sm font-semibold text-primary hover:underline"
          >
            {emptyActionLabel}
          </Link>
        </div>
      )}
    </section>
  )
}

function MyItemCard({ item, serverOffsetMs }: {
  item: ItemCardModel
  serverOffsetMs: number
}) {
  const isDown = item.auctionType === 'DOWN'

  return (
    <Link
      to={`/auctions/${item.auctionId}`}
      className={`flex h-full flex-col gap-sm p-sm transition-shadow hover:shadow-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${TILE_SURFACE_CLASS}`}
    >
      <div className="flex items-start justify-between gap-xs">
        <p className="line-clamp-1 text-sm font-bold text-ink">{item.title}</p>
        <Badge tone={item.statusTone}>{item.statusLabel}</Badge>
      </div>

      <div className="flex gap-sm">
        <ItemThumbnail thumbnailUrl={item.thumbnailUrl} className={THUMBNAIL_CLASS} />

        <dl className="flex min-w-0 flex-1 flex-col justify-center gap-0.5">
          <div className="flex items-baseline justify-between gap-xs">
            <dt className="shrink-0 text-[11px] text-muted">{ITEM_CARD_TEXT.startPriceLabel}</dt>
            <dd className="truncate text-xs text-muted">{formatWon(item.startPrice)}</dd>
          </div>
          <div className="flex items-baseline justify-between gap-xs">
            <dt className="shrink-0 text-[11px] text-muted">
              {item.isSettled ? ITEM_CARD_TEXT.finalPriceLabel : ITEM_CARD_TEXT.currentPriceLabel}
            </dt>
            <dd className={`truncate text-base font-bold ${isDown ? 'text-down' : 'text-ink'}`}>
              {item.downPricing ? (
                <DownCurrentPrice pricing={item.downPricing} serverOffsetMs={serverOffsetMs} />
              ) : (
                <RollingPrice value={item.price} />
              )}
            </dd>
          </div>
        </dl>
      </div>
    </Link>
  )
}

function DownCurrentPrice({ pricing, serverOffsetMs }: {
  pricing: DownPricing
  serverOffsetMs: number
}) {
  const { currentPrice } = useDownAuctionClock(pricing, serverOffsetMs)
  return <RollingPrice value={currentPrice} />
}

export default MyItemSection
