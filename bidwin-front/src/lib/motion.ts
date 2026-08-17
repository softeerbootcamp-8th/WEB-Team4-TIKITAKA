import type { CSSProperties } from 'react'

/*
 * 실시간 변화를 눈에 보이게 하는 연출을 한곳에 모은다. 실제 keyframes는 styles/base.css에
 * 있고, 여기서는 어떤 상황에 얼마나 재생할지만 정한다(화면마다 시간이 달라지지 않게).
 */

const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)'

/** 움직임 줄이기를 켠 사용자에게는 위치·크기가 움직이는 연출을 생략하고 색 신호만 남긴다. */
export function prefersReducedMotion() {
  return typeof window !== 'undefined'
    && window.matchMedia(REDUCED_MOTION_QUERY).matches
}

/** 마감된 뒤 연출을 유지하는 시간. useRecentChange에 그대로 넘겨 쓴다. */
export const CLOSE_HIGHLIGHT_MS = 1_200
const CLOSE_POP_DURATION_MS = 420

/** 마감 배지·안내가 자리에 나타나는 연출. */
export function closePopStyle(isActive: boolean): CSSProperties | undefined {
  if (!isActive || prefersReducedMotion()) return undefined
  return { animation: `auction-close-pop ${CLOSE_POP_DURATION_MS}ms ease-out` }
}

/** 마감된 카드를 한 번 훑는 음영. 색만 바뀌므로 움직임 줄이기와 무관하게 보여준다. */
export function closeSweepStyle(isActive: boolean): CSSProperties | undefined {
  if (!isActive) return undefined
  return { animation: `auction-close-sweep ${CLOSE_HIGHLIGHT_MS}ms ease-out` }
}
