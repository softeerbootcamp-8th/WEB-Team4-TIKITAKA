import { PRICE_FLASH_DURATION_MS, usePriceRoll } from '../../hooks/usePriceRoll'
import type { PriceRollDirection } from '../../hooks/usePriceRoll'
import { formatWon } from '../../lib/format'

const FLASH_ANIMATION: Record<PriceRollDirection, string> = {
  up: `price-roll-up ${PRICE_FLASH_DURATION_MS}ms ease-out`,
  down: `price-roll-down ${PRICE_FLASH_DURATION_MS}ms ease-out`,
}

interface RollingPriceProps {
  value: number
  /* 글자 크기·색은 쓰는 자리에서 정한다. 굴러가는 중간값이 아니라 확정된 값 기준으로 계산할 것. */
  className?: string
}

/*
 * 실시간으로 바뀌는 금액. 값이 갈릴 때 이전 금액에서 새 금액까지 숫자가 굴러가고,
 * 오른 쪽·내린 쪽 색이 잠깐 남는다. 입찰·즉시구매·하락처럼 SSE로 넘어오는 변화를
 * 텍스트만 바뀌는 대신 눈에 걸리게 만드는 것이 목적이다.
 * 굴러가는 동안의 숫자는 중간값이라 스크린리더에는 확정된 값만 읽어준다.
 */
function RollingPrice({ value, className = '' }: RollingPriceProps) {
  const { display, direction } = usePriceRoll(value)

  return (
    <span className={className}>
      <span
        aria-hidden
        className="-mx-1 inline-block rounded-xs px-1 tabular-nums"
        style={direction === null ? undefined : { animation: FLASH_ANIMATION[direction] }}
      >
        {formatWon(display)}
      </span>
      <span className="sr-only">{formatWon(value)}</span>
    </span>
  )
}

export default RollingPrice
export type { RollingPriceProps }
