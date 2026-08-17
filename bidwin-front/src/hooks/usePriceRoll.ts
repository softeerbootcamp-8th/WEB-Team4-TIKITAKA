import { useEffect, useRef, useState } from 'react'
import { prefersReducedMotion } from '../lib/motion'

/*
 * 값이 바뀔 때 숫자를 굴려서 보여준다. SSE로 가격이 갈리면 텍스트만 "띡" 바뀌어
 * 사용자가 변화를 놓치므로, 이전 값에서 새 값까지 짧게 보간해 눈에 걸리게 만든다.
 */

/** 굴러가는 시간. 이보다 길면 연속 입찰에서 다음 갱신과 겹쳐 값이 계속 흐려 보인다. */
export const PRICE_ROLL_DURATION_MS = 300
/** 굴리기가 끝난 뒤에도 색 신호를 잠깐 남겨 어디가 바뀌었는지 알아볼 시간을 준다. */
export const PRICE_FLASH_DURATION_MS = 900
/*
 * 백그라운드 탭이나 가려진 창에서는 requestAnimationFrame이 멈춰 굴리기가 끝나지 않는다.
 * 표시 금액이 옛값에 머무는 일이 없도록, 굴릴 시간이 지나면 타이머로 확정값에 맞춘다.
 */
const SETTLE_MARGIN_MS = 120

export type PriceRollDirection = 'up' | 'down'

/* 처음 빠르게 돌다가 끝에서 붙는 감속. 마지막 자리 숫자가 정착하는 게 보인다. */
function easeOut(progress: number) {
  return 1 - (1 - progress) ** 3
}

export function usePriceRoll(value: number) {
  const [display, setDisplay] = useState(value)
  const [direction, setDirection] = useState<PriceRollDirection | null>(null)
  /* 굴러가던 중간값에서 다시 출발해야 연속으로 갱신돼도 숫자가 튀지 않는다. */
  const displayRef = useRef(value)

  useEffect(() => {
    const from = displayRef.current
    if (from === value) return

    setDirection(value > from ? 'up' : 'down')
    const flashTimer = window.setTimeout(
      () => setDirection(null),
      PRICE_FLASH_DURATION_MS,
    )

    const settle = () => {
      displayRef.current = value
      setDisplay(value)
    }

    /* 움직임을 줄인 사용자와 보이지 않는 탭에서는 굴리지 않고 값만 맞춘다. */
    if (prefersReducedMotion() || document.hidden) {
      settle()
      return () => window.clearTimeout(flashTimer)
    }

    const startedAt = performance.now()
    let frame = 0
    const step = (now: number) => {
      const progress = Math.min(1, (now - startedAt) / PRICE_ROLL_DURATION_MS)
      if (progress === 1) {
        settle()
        return
      }
      const next = Math.round(from + (value - from) * easeOut(progress))
      displayRef.current = next
      setDisplay(next)
      frame = requestAnimationFrame(step)
    }
    frame = requestAnimationFrame(step)
    /* 프레임이 오지 않는 환경(백그라운드·가려진 창)에서도 확정값으로 맞춰준다. */
    const settleTimer = window.setTimeout(settle, PRICE_ROLL_DURATION_MS + SETTLE_MARGIN_MS)

    return () => {
      window.clearTimeout(flashTimer)
      window.clearTimeout(settleTimer)
      cancelAnimationFrame(frame)
    }
  }, [value])

  return { display, direction }
}
