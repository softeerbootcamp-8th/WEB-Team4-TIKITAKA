import type { RouteObject } from 'react-router-dom'

/*
 * 페이지 목록. 새 페이지를 추가할 때:
 * 1. src/routes/<path>/page.tsx 파일을 만든다.
 * 2. 아래 배열에 한 줄 추가한다 (path와 lazy import 경로만 맞추면 됨).
 */
export const pageRoutes: RouteObject[] = [
  { index: true, lazy: () => import('../routes/page') },
  { path: 'login', lazy: () => import('../routes/login/page') },
  { path: 'signup', lazy: () => import('../routes/signup/page') },
  { path: 'auctions', lazy: () => import('../routes/auctions/page') },
  { path: 'auctions/new', lazy: () => import('../routes/auctions/new/page') },
  { path: 'auctions/:auctionId', lazy: () => import('../routes/auctions/detail/page') },
  { path: 'mypage', lazy: () => import('../routes/mypage/page') },
  { path: 'mypage/password', lazy: () => import('../routes/mypage/password/page') },
  { path: 'mypage/history', lazy: () => import('../routes/mypage/history/page') },
  { path: 'trades/:tradeId', lazy: () => import('../routes/trades/detail/page') },
  { path: 'email-verification', lazy: () => import('../routes/email-verification/page') },
  { path: 'password-reset', lazy: () => import('../routes/password-reset/page') },
  {
    path: 'password-reset/confirm',
    lazy: () => import('../routes/password-reset/confirm/page'),
  },
].map((route) => ({
  ...route,
  lazy: async () => ({ Component: (await route.lazy()).default }),
}))
