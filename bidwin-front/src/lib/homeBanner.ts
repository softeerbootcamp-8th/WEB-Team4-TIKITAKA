export const HOME_BANNER_ITEMS = [
  { label: '이불', emoji: '💤' },
  { label: '1+1 음식', emoji: '🍱' },
  { label: '매트리스', emoji: '🛏️' },
  { label: '책상', emoji: '📚' },
  { label: '무드등', emoji: '💡' },
  { label: '건조기', emoji: '🧺' },
] as const
export const HOME_BANNER_ROTATION_MS = 3000
export const HOME_BANNER_TRANSITION_MS = 1000

export function nextHomeBannerIndex(index: number): number {
  return (index + 1) % HOME_BANNER_ITEMS.length
}

export function homeBannerTransitionDuration(isRolling: boolean): string {
  return isRolling ? `${HOME_BANNER_TRANSITION_MS}ms` : '0ms'
}
