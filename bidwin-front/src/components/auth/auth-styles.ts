/*
 * 인증 화면(로그인·회원가입)의 링크형 버튼도 Button과 같은 포커스·눌림 반응을 갖도록 맞춘다.
 * 두 화면이 서로 달라지지 않게 한 곳에서만 정의한다.
 */
export const LINK_INTERACTION_CLASSES =
  'rounded-xs transition-colors active:opacity-70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-canvas'
