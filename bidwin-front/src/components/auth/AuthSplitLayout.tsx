import type { ReactNode } from 'react'
import AuthShowcase from './AuthShowcase'
import type { AuthShowcaseVariant } from './AuthShowcase'

/* TopNav(h-16 = 4rem)를 뺀 나머지 화면 전체를 차지한다. */
const FULL_HEIGHT_BELOW_TOP_NAV = 'min-h-[calc(100dvh-4rem)]'

interface AuthSplitLayoutProps {
  /* 좌측 폼 영역. 로그인 ↔ 회원가입 전환 시 이 부분만 바뀐다. */
  children: ReactNode
  /* 우측 소개 패널의 문구를 고르는 값(카드·혜택 목록은 두 화면이 같다) */
  variant: AuthShowcaseVariant
}

/*
 * 로그인·회원가입이 공유하는 좌우 2단 껍데기.
 * 좌측은 폼, 우측은 서비스 소개 패널이며 두 화면의 여백·경계·정렬을 항상 같게 유지한다.
 */
function AuthSplitLayout({ children, variant }: AuthSplitLayoutProps) {
  return (
    <main className={`grid md:grid-cols-2 ${FULL_HEIGHT_BELOW_TOP_NAV}`}>
      <section className="flex items-center justify-center px-lg py-xxl">
        {children}
      </section>

      <AuthShowcase variant={variant} />
    </main>
  )
}

export default AuthSplitLayout
export type { AuthSplitLayoutProps }
