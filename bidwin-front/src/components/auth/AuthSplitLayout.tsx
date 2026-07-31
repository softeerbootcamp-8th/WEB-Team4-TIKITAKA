import type { ReactNode } from 'react'

/* TopNav(h-16 = 4rem)를 뺀 나머지 화면 전체를 차지한다. */
const FULL_HEIGHT_BELOW_TOP_NAV = 'min-h-[calc(100dvh-4rem)]'

interface AuthSplitLayoutProps {
  /* 좌측 폼 영역. 로그인 ↔ 회원가입 전환 시 이 부분만 바뀐다. */
  children: ReactNode
  /* 우측 이미지 영역 자리표시 문구(이미지 준비 전까지 화면별로 다른 값을 넣는다) */
  imagePlaceholder: string
}

/*
 * 로그인·회원가입이 공유하는 좌우 2단 껍데기.
 * 좌측은 폼, 우측은 이미지 영역이며 두 화면의 여백·경계·정렬을 항상 같게 유지한다.
 */
function AuthSplitLayout({ children, imagePlaceholder }: AuthSplitLayoutProps) {
  return (
    <main className={`grid md:grid-cols-2 ${FULL_HEIGHT_BELOW_TOP_NAV}`}>
      <section className="flex items-center justify-center px-lg py-xxl">
        {children}
      </section>

      {/* 우측 이미지는 아직 비워둔다 (자리만 확보) */}
      <div
        aria-hidden
        className="hidden items-center justify-center border-l border-hairline-soft bg-surface-soft md:flex"
      >
        <span className="text-sm text-muted-soft">{imagePlaceholder}</span>
      </div>
    </main>
  )
}

export default AuthSplitLayout
export type { AuthSplitLayoutProps }
