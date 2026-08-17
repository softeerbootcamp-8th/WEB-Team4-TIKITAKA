import { Clock, ShieldCheck, TrendingDown, TrendingUp } from 'lucide-react'
import { useEffect, useState } from 'react'
import { formatClock, formatWon } from '../../lib/format'
import Badge from '../ui/Badge'

type AuthShowcaseVariant = 'login' | 'signup'

/* 상향 경매는 마감 이 시간 전부터 밀봉 구간이다(서비스 규칙). */
const SEALED_BID_WINDOW_MINUTES = 5

const DEMO_AUCTION = {
  emoji: '🛏️',
  category: '가구',
  title: '거의 새것 퀸사이즈 매트리스',
  seller: '오늘이사',
}

const DEMO_START_PRICE = 200000
const DEMO_MINIMUM_PRICE = 120000
const DEMO_DROP_PRICE = 10000
/*
 * 실제 인하 주기는 1·3·5·10분이지만, 소개용 카드에서 몇 분씩 기다리게 할 수는 없어
 * 하락 리듬만 보이도록 초 단위로 압축했다. 가격 정책 자체(시작가 → 인하 → 최저가)는 같다.
 */
const DEMO_DROP_INTERVAL_SECONDS = 5
/* 최저가 도달을 보여준 뒤 데모를 처음부터 다시 돌리기까지의 시간 */
const DEMO_FLOOR_HOLD_SECONDS = 6
const DEMO_TICK_MS = 1000
const PRICE_DROP_DURATION_MS = 400
const PERCENT_BASE = 100
const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)'

const TEXT = {
  dropping: '하락 중',
  atFloorBadge: '최저가',
  auctionType: '하향 경매',
  priceLabel: '지금 이 가격',
  nextDropSuffix: '후 추가 하락',
  atFloorNotice: '최저가에 도달했어요',
  droppedPrefix: '시작가 대비',
  droppedSuffix: '하락',
}

const HEADLINE: Record<AuthShowcaseVariant, { lines: string[]; description: string }> = {
  login: {
    lines: ['기다릴수록', '가격은 내려갑니다'],
    description: '로그인하고 지금 진행 중인 경매에 바로 참여하세요.',
  },
  signup: {
    lines: ['오늘 급하게 나온 물건을', '가장 먼저 만나보세요'],
    description: '가입을 마치면 입찰도 판매도 바로 시작할 수 있어요.',
  },
}

const BENEFITS = [
  {
    icon: TrendingDown,
    title: '기다릴수록 싸지는 하향 경매',
    description: '정해진 주기마다 가격이 내려가요. 원하는 가격일 때 바로 잡으세요.',
  },
  {
    icon: TrendingUp,
    title: '경쟁이 붙으면 오르는 상향 경매',
    description: `입찰이 몰릴수록 값이 오르고, 마감 ${SEALED_BID_WINDOW_MINUTES}분 전엔 밀봉 입찰로 바뀌어요.`,
  },
  {
    icon: ShieldCheck,
    title: '본인인증과 보증금',
    description: '검증된 사용자끼리, 묶어둔 보증금으로 거래해요.',
  },
]

const BENEFIT_ICON_SIZE = 16
const META_ICON_SIZE = 12

interface DemoState {
  price: number
  /* 다음 인하까지 남은 시간(초) */
  untilNextDrop: number
  /* 최저가에 닿은 뒤 흐른 시간(초). 이 값이 차면 데모를 처음부터 다시 돌린다. */
  floorFor: number
}

const INITIAL_DEMO_STATE: DemoState = {
  price: DEMO_START_PRICE,
  untilNextDrop: DEMO_DROP_INTERVAL_SECONDS,
  floorFor: 0,
}

function nextDemoState(state: DemoState): DemoState {
  if (state.price <= DEMO_MINIMUM_PRICE) {
    return state.floorFor >= DEMO_FLOOR_HOLD_SECONDS
      ? INITIAL_DEMO_STATE
      : { ...state, floorFor: state.floorFor + 1 }
  }

  if (state.untilNextDrop > 1) {
    return { ...state, untilNextDrop: state.untilNextDrop - 1 }
  }

  return {
    price: Math.max(DEMO_MINIMUM_PRICE, state.price - DEMO_DROP_PRICE),
    untilNextDrop: DEMO_DROP_INTERVAL_SECONDS,
    floorFor: 0,
  }
}

/*
 * 움직임을 줄이도록 설정한 사용자에게는 시계를 돌리지 않고 첫 상태만 보여준다.
 * 카드가 비지는 않으므로 화면 구성은 그대로 유지된다.
 */
function useDemoDownAuction() {
  const [state, setState] = useState(INITIAL_DEMO_STATE)

  useEffect(() => {
    if (window.matchMedia(REDUCED_MOTION_QUERY).matches) return

    const timer = window.setInterval(() => setState(nextDemoState), DEMO_TICK_MS)
    return () => window.clearInterval(timer)
  }, [])

  return state
}

interface AuthShowcaseProps {
  variant: AuthShowcaseVariant
}

/*
 * 로그인·회원가입 우측 패널. 폼 반대편을 비워두지 않고, 하향 경매 한 건이 실제로
 * 어떻게 떨어지는지를 그대로 보여준다.
 * 실제 데이터가 아니라 소개용 장식이므로 스크린 리더에는 노출하지 않는다.
 */
function AuthShowcase({ variant }: AuthShowcaseProps) {
  const { price, untilNextDrop } = useDemoDownAuction()
  const headline = HEADLINE[variant]
  const isAtFloor = price <= DEMO_MINIMUM_PRICE
  const dropped = DEMO_START_PRICE - price
  const droppedPercent = (dropped / (DEMO_START_PRICE - DEMO_MINIMUM_PRICE)) * PERCENT_BASE

  return (
    <aside
      aria-hidden
      className="hidden items-center justify-center border-l border-hairline-soft bg-surface-soft px-xl py-xl md:flex"
    >
      <div className="flex w-full max-w-[420px] flex-col gap-lg">
        <div className="flex flex-col gap-sm">
          <h2 className="text-[clamp(1.75rem,2.4vw,2.25rem)] font-bold leading-[1.15] tracking-[-0.025em] text-ink">
            {headline.lines.map((line) => (
              <span key={line} className="block">
                {line}
              </span>
            ))}
          </h2>
          <p className="text-base text-body">{headline.description}</p>
        </div>

        <article className="flex flex-col gap-base rounded-xl border border-hairline-soft bg-canvas p-lg shadow-card">
          <div className="flex items-center gap-xs">
            <Badge tone={isAtFloor ? 'muted' : 'live'}>
              {isAtFloor ? TEXT.atFloorBadge : TEXT.dropping}
            </Badge>
            <span className="inline-flex h-7 items-center gap-1 rounded-pill bg-down-tint px-sm text-xs font-semibold text-down">
              <TrendingDown size={META_ICON_SIZE} />
              {TEXT.auctionType}
            </span>
          </div>

          <div className="flex items-center gap-base">
            <span className="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg bg-surface-soft text-3xl">
              {DEMO_AUCTION.emoji}
            </span>
            <div className="min-w-0">
              <p className="truncate text-xs font-semibold text-primary">
                {DEMO_AUCTION.category}
              </p>
              <h3 className="truncate text-base font-bold text-ink">{DEMO_AUCTION.title}</h3>
              <p className="truncate text-xs text-muted">{DEMO_AUCTION.seller}</p>
            </div>
          </div>

          <div className="flex items-end justify-between gap-sm border-t border-hairline-soft pt-base">
            <div className="min-w-0">
              <p className="text-xs text-muted">{TEXT.priceLabel}</p>
              {/* 가격이 한 칸 떨어질 때마다 key가 바뀌면서 값이 떨어지는 효과가 재생된다. */}
              <p
                key={price}
                className="flex items-center gap-1 text-xl font-bold tracking-tight text-down"
                style={{ animation: `price-step-down ${PRICE_DROP_DURATION_MS}ms ease-out` }}
              >
                {formatWon(price)}
                {!isAtFloor && <TrendingDown size={18} className="shrink-0" />}
              </p>
            </div>
            <span className="inline-flex h-7 shrink-0 items-center gap-1 whitespace-nowrap rounded-pill bg-surface-strong px-sm text-xs font-semibold text-body">
              {isAtFloor ? (
                TEXT.atFloorNotice
              ) : (
                <>
                  <Clock size={META_ICON_SIZE} />
                  <span className="font-mono">{formatClock(untilNextDrop)}</span>
                  {TEXT.nextDropSuffix}
                </>
              )}
            </span>
          </div>

          <div>
            <div className="h-2 w-full overflow-hidden rounded-full bg-surface-strong">
              <div
                className="h-full rounded-full bg-down transition-[width] duration-500 ease-out"
                style={{ width: `${droppedPercent}%` }}
              />
            </div>
            {/* 아직 안 떨어졌을 때도 자리를 지켜, 첫 인하에 카드 높이가 흔들리지 않게 한다. */}
            <p
              className={`mt-1.5 flex items-center gap-1 text-xs font-semibold text-down transition-opacity ${dropped > 0 ? 'opacity-100' : 'opacity-0'}`}
            >
              <TrendingDown size={META_ICON_SIZE} />
              {TEXT.droppedPrefix} {formatWon(dropped)} {TEXT.droppedSuffix}
            </p>
          </div>
        </article>

        <ul className="flex flex-col gap-base">
          {BENEFITS.map(({ icon: Icon, title, description }) => (
            <li key={title} className="flex items-start gap-sm">
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-canvas text-body">
                <Icon size={BENEFIT_ICON_SIZE} />
              </span>
              <div className="min-w-0">
                <p className="text-sm font-semibold text-ink">{title}</p>
                <p className="text-xs text-body">{description}</p>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </aside>
  )
}

export default AuthShowcase
export type { AuthShowcaseProps, AuthShowcaseVariant }
