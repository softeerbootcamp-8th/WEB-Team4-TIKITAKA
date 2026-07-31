import { Bookmark, Clock, Eye, Gavel, ImageIcon, TrendingDown, TrendingUp } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useCountdown } from '../../../hooks/useCountdown'
import { formatClock, formatWon } from '../../../lib/format'
import { CARD_HASHTAG_LIMIT, CARD_TEXT } from '../constants'
import type { AuctionSummary } from '../types'

/*
 * 상향/하향 경매는 같은 틀 안에서 색과 보조 정보로 구분한다.
 * 하향은 가격이 떨어지는 중이라 시작가 대비 하락폭을, 상향은 입찰 경쟁 정도를 보여준다.
 */
const TYPE_LABEL = {
  UP: '상향 경매',
  DOWN: '하향 경매',
} as const

const PERCENT_BASE = 100

/*
 * 한 컴포넌트가 두 형태를 그린다. md(768px)를 모바일 경계로 본다.
 *   모바일  — 좌측 이미지 + 우측 정보의 가로 리스트 행 (게시글 목록 형태)
 *   그 이상 — 이미지가 위, 정보가 아래인 세로 카드 (한 행에 4개)
 * JS로 화면 폭을 재지 않고 반응형 클래스만 쓴다. 리사이즈·초기 렌더에서 깜빡임이 없다.
 */
const THUMBNAIL_CLASS = 'h-28 w-28 shrink-0 md:h-auto md:w-full md:aspect-square'

/*
 * 공통 Card(components/ui/Card)는 p-xl이 박혀 있어 className으로 패딩을 줄일 수 없다.
 * Tailwind는 같은 속성이 겹치면 클래스 문자열 순서가 아니라 생성 순서로 이기는데,
 * theme.css의 spacing 선언 순서상 p-xl이 p-sm보다 뒤라 항상 p-xl이 적용된다.
 * 카드가 촘촘한 목록이라 외형은 Card와 똑같이 두고 패딩만 직접 잡는다.
 */
const CARD_SURFACE_CLASS = 'rounded-xl border border-hairline-soft bg-canvas'

/*
 * 경매 종류 배지. 놓이는 자리에 따라 배경만 달라진다.
 *   overlay — 세로 카드에서 이미지 위에 얹을 때 (사진과 겹치므로 반투명 + blur)
 *   inline  — 리스트 행에서 하단 줄에 나란히 놓을 때
 */
function AuctionTypeBadge({
  auctionType,
  tone,
}: {
  auctionType: AuctionSummary['auctionType']
  tone: 'overlay' | 'inline'
}) {
  const isDown = auctionType === 'DOWN'
  const toneClass = isDown
    ? tone === 'overlay'
      ? 'bg-down-tint/95 text-down backdrop-blur-sm'
      : 'bg-down-tint text-down'
    : tone === 'overlay'
      ? 'bg-canvas/90 text-body backdrop-blur-sm'
      : 'bg-surface-strong text-body'

  return (
    <span
      className={`inline-flex h-6 shrink-0 items-center gap-1 whitespace-nowrap rounded-pill px-2 text-[11px] font-semibold ${toneClass}`}
    >
      {isDown ? <TrendingDown size={11} /> : <TrendingUp size={11} />}
      {TYPE_LABEL[auctionType]}
    </span>
  )
}

function AuctionCard({
  auction,
  isBookmarked,
  onToggleBookmark,
}: {
  auction: AuctionSummary
  isBookmarked: boolean
  onToggleBookmark: (auctionId: number) => void
}) {
  const { remaining, isUrgent, isEnded } = useCountdown(auction.deadline)
  const isDown = auction.auctionType === 'DOWN'
  const dropRate = Math.round(
    ((auction.startPrice - auction.currentPrice) / auction.startPrice) * PERCENT_BASE,
  )

  return (
    <article
      className={`relative flex gap-sm p-sm transition-shadow hover:shadow-card md:flex-col ${CARD_SURFACE_CLASS}`}
    >
      {/* 카드 전체를 누르면 상세로 간다. 북마크 버튼만 이 링크 위에 올려 둔다. */}
      <Link
        to={`/auctions/${auction.auctionId}`}
        aria-label={auction.title}
        className="absolute inset-0 z-10 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      />

      {/* 세로 카드에서는 이미지가 카드 폭을 채우므로 이 위치가 곧 이미지 우상단이 된다. */}
      <button
        type="button"
        onClick={() => onToggleBookmark(auction.auctionId)}
        aria-label={isBookmarked ? CARD_TEXT.bookmarkOn : CARD_TEXT.bookmarkOff}
        aria-pressed={isBookmarked}
        className={`absolute right-4 top-4 z-20 flex h-7 w-7 items-center justify-center rounded-full bg-canvas/80 backdrop-blur-sm transition-colors hover:bg-canvas ${
          isBookmarked ? 'text-ink' : 'text-muted'
        }`}
      >
        <Bookmark size={16} fill={isBookmarked ? 'currentColor' : 'none'} />
      </button>

      <div className={`relative ${THUMBNAIL_CLASS}`}>
        {auction.thumbnailUrl ? (
          <img
            src={auction.thumbnailUrl}
            alt=""
            className="h-full w-full rounded-lg object-cover"
          />
        ) : (
          <span className="flex h-full w-full items-center justify-center rounded-lg bg-surface-soft text-muted-soft">
            <ImageIcon size={28} />
          </span>
        )}
        {/*
          세로 카드는 하단 줄에 배지 두 개가 들어가지 않아 경매 종류를 이미지 위에 얹는다.
          리스트 행에서는 썸네일이 작아 배지가 사진을 다 덮으므로 하단 줄로 내린다.
        */}
        <div className="absolute left-2 top-2 hidden md:block">
          <AuctionTypeBadge auctionType={auction.auctionType} tone="overlay" />
        </div>
      </div>

      {/* 모바일에서는 이미지 오른쪽 칸, 그 이상에서는 이미지 아래 칸 */}
      <div className="flex min-w-0 flex-1 flex-col gap-1 md:mt-1 md:flex-none">
        <div className="min-w-0 pr-8 md:pr-0">
          <p className="line-clamp-1 text-xs font-semibold text-ink">{auction.sellerName}</p>
          <p className="line-clamp-1 text-[11px] text-primary">
            {auction.category} · {auction.region}
          </p>
        </div>

        <h2 className="line-clamp-2 text-sm font-bold leading-snug text-ink md:min-h-[2.75em]">
          {auction.title}
        </h2>

        {/* 카드가 좁아지면 보조 정보가 다음 줄로 내려가게 두고, 금액 자체는 절대 쪼개지 않는다. */}
        <div className="flex flex-wrap items-end gap-x-1">
          <span
            className={`whitespace-nowrap text-base font-bold ${isDown ? 'text-down' : 'text-ink'}`}
          >
            {formatWon(auction.currentPrice)}
          </span>
          {isDown ? (
            <span className="flex items-center gap-0.5 whitespace-nowrap pb-0.5 text-[11px] font-semibold text-down">
              <TrendingDown size={11} />
              {dropRate}%
            </span>
          ) : (
            <span className="flex items-center gap-0.5 whitespace-nowrap pb-0.5 text-[11px] text-muted">
              <Gavel size={11} />
              {auction.bidCount > 0
                ? `${auction.bidCount}${CARD_TEXT.bidCountSuffix}`
                : CARD_TEXT.noBid}
            </span>
          )}
        </div>

        {/* 리스트 행에서는 높이를 이미지에 맞춰야 해서 해시태그는 접는다. */}
        <p className="hidden line-clamp-1 text-[11px] text-muted md:block">
          {auction.hashtags
            .slice(0, CARD_HASHTAG_LIMIT)
            .map((tag) => `#${tag}`)
            .join(' ')}
        </p>

        {/* 배지는 안에서 줄바꿈되지 않으니, 폭이 모자라면 줄 단위로 넘긴다(가로 넘침 방지). */}
        <div className="mt-auto flex flex-wrap items-center gap-1.5 pt-1">
          <div className="md:hidden">
            <AuctionTypeBadge auctionType={auction.auctionType} tone="inline" />
          </div>
          <span
            className={`inline-flex h-6 shrink-0 items-center gap-1 whitespace-nowrap rounded-pill px-2 text-[11px] font-semibold ${
              isEnded
                ? 'bg-surface-strong text-muted'
                : isUrgent
                  ? 'bg-down text-on-primary'
                  : 'bg-surface-strong text-body'
            }`}
          >
            <Clock size={11} className="shrink-0" />
            {isEnded ? CARD_TEXT.ended : formatClock(remaining)}
            {/* 리스트 행에서는 배지 두 개가 나란히 서서 폭이 빠듯해 "남음"을 접는다. */}
            {!isEnded && (
              <span className="hidden md:inline">{CARD_TEXT.remainingSuffix}</span>
            )}
          </span>
          {/*
            리스트 행은 폭이 좁아 배지 두 개까지만 한 줄에 들어간다.
            조회수는 목록에서 가장 덜 중요한 정보라 여기서만 접는다.
          */}
          <span
            className="ml-auto hidden shrink-0 items-center gap-1 text-[11px] text-muted md:flex"
            aria-label={CARD_TEXT.viewCountLabel}
          >
            <Eye size={12} />
            {auction.viewCount}
          </span>
        </div>
      </div>
    </article>
  )
}

export default AuctionCard
