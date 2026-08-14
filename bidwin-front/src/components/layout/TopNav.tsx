import { LogOut, Menu, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, createSearchParams, useLocation, useNavigate } from 'react-router-dom'
import bidwinLogo from '../../assets/bidwin-logo.png'
import { useAuth } from '../../hooks/useAuth'
import { useToast } from '../../hooks/useToast'
import { requestLogout } from '../../lib/api/auth'
import { requestMyPage } from '../../lib/api/mypage'
import { formatWon } from '../../lib/format'
import Button from '../ui/Button'
import Modal from '../ui/Modal'

const NAV_LINKS = [
  { label: '진행중 경매', to: '/auctions?status=ACTIVE', pathname: '/auctions', authenticatedOnly: false },
  { label: '판매하기', to: '/auctions/new', pathname: '/auctions/new', authenticatedOnly: true },
]

const AVAILABLE_DEPOSIT_LABEL = '사용 가능 금액'

function TopNav() {
  const { isAuthenticated, setAuthenticated } = useAuth()
  const { showToast } = useToast()
  const location = useLocation()
  const navigate = useNavigate()
  const currentKeyword = location.pathname === '/auctions'
    ? new URLSearchParams(location.search).get('q') ?? ''
    : ''
  const [keyword, setKeyword] = useState(currentKeyword)
  const [isSearchOpen, setIsSearchOpen] = useState(false)
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const [isLoggingOut, setIsLoggingOut] = useState(false)
  /* 지금 입찰에 쓸 수 있는 금액. inUse(입찰에 묶인 보증금)는 뺀 deposit.balance다. */
  const [availableDeposit, setAvailableDeposit] = useState<number | null>(null)

  useEffect(() => setKeyword(currentKeyword), [currentKeyword])
  useEffect(() => setIsMenuOpen(false), [location.pathname, location.search])
  useEffect(() => {
    const mediaQuery = window.matchMedia('(min-width: 768px)')

    const closeOnDesktop = (event: MediaQueryListEvent) => {
      if (event.matches) setIsMenuOpen(false)
    }

    if (mediaQuery.matches) setIsMenuOpen(false)
    mediaQuery.addEventListener('change', closeOnDesktop)

    return () => {
      mediaQuery.removeEventListener('change', closeOnDesktop)
    }
  }, [])
  useEffect(() => {
    if (isAuthenticated !== true) {
      setAvailableDeposit(null)
      return
    }

    const controller = new AbortController()
    requestMyPage(controller.signal).then((result) => {
      if (controller.signal.aborted) return
      if (result.ok) {
        setAvailableDeposit(result.data.deposit.balance)
        return
      }
      if (result.status === 401) setAuthenticated(false)
    })

    return () => {
      controller.abort()
    }
  }, [isAuthenticated, setAuthenticated])

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const normalized = keyword.trim()
    navigate({
      pathname: '/auctions',
      search: normalized ? `?${createSearchParams({ q: normalized })}` : '',
    })
    setIsSearchOpen(false)
  }

  async function handleLogout() {
    if (isLoggingOut) return
    setIsLoggingOut(true)
    const result = await requestLogout()
    setIsLoggingOut(false)

    /* 이미 만료된 세션의 401도 클라이언트에서는 로그아웃 완료 상태다. */
    if (!result.ok && result.status !== 401) {
      showToast(result.message, 'info')
      return
    }

    setAuthenticated(false)
    navigate('/', { replace: true })
    showToast('로그아웃됐어요.')
  }

  const searchField = (
    <div className="relative flex-1">
      <Search
        size={16}
        aria-hidden
        className="pointer-events-none absolute left-sm top-1/2 -translate-y-1/2 text-muted"
      />
      <input
        type="search"
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
        placeholder="경매 검색"
        aria-label="경매 검색어"
        maxLength={30}
        className="h-10 w-full rounded-pill border border-hairline bg-surface-soft pl-9 pr-base text-sm text-ink outline-none focus:border-primary focus:bg-canvas"
      />
    </div>
  )

  return (
    <header className="relative h-16 sticky top-0 z-40 border-b border-hairline bg-canvas">
      <div className="mx-auto flex h-full max-w-[1200px] items-center gap-sm px-base sm:gap-xl sm:px-lg">
        <Link to="/" aria-label="비드윈 홈" className="shrink-0">
          <img src={bidwinLogo} alt="" className="block h-8 w-auto sm:h-10" />
        </Link>
        <nav className="hidden flex-1 items-center gap-md md:flex">
          {NAV_LINKS.filter((link) => !link.authenticatedOnly || isAuthenticated === true).map((link) => {
            const isActive = location.pathname === link.pathname
            return (
              <Link
                key={link.to}
                to={link.to}
                aria-current={isActive ? 'page' : undefined}
                className={`relative flex items-center whitespace-nowrap text-sm font-medium transition-colors after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:origin-left after:bg-primary after:transition-transform after:duration-200 ${
                  isActive
                    ? 'text-primary after:scale-x-100'
                    : 'text-body after:scale-x-0 hover:text-primary hover:after:scale-x-100'
                }`}
              >
                {link.label}
              </Link>
            )
          })}
        </nav>
        <div className="ml-auto flex min-w-0 items-center gap-xs sm:gap-sm">
          <form onSubmit={submitSearch} role="search" className="hidden w-[min(280px,30vw)] md:flex">
            {searchField}
          </form>
          <div className="flex shrink-0 items-center gap-xs">
            <button
              type="button"
              aria-label="검색"
              onClick={() => setIsSearchOpen(true)}
              className="flex h-9 w-9 items-center justify-center rounded-full text-body hover:bg-surface-strong hover:text-ink md:hidden"
            >
              <Search size={18} />
            </button>
            {isAuthenticated === true ? (
              <>
                {/*
                  * 알약 버튼 사이에 또 하나의 알약을 두면 무게가 겹쳐, 라벨+금액 두 줄로 세우고
                  * 오른쪽 hairline으로만 버튼 묶음과 나눈다. 값이 늦게 와도 헤더가 밀리지 않게
                  * 로딩 중에는 같은 폭의 자리를 잡아 둔다.
                  */}
                <p className="mr-xxs hidden flex-col items-end justify-center gap-0.5 border-r border-hairline pr-sm leading-none md:flex">
                  <span className="text-xs font-medium text-muted">{AVAILABLE_DEPOSIT_LABEL}</span>
                  {availableDeposit === null ? (
                    <span aria-hidden className="h-4 w-20 animate-pulse rounded-xs bg-surface-strong" />
                  ) : (
                    <span className="text-sm font-bold tracking-tight text-ink tabular-nums">
                      {formatWon(availableDeposit)}
                    </span>
                  )}
                </p>
                <Link
                  to="/mypage"
                  className="hidden h-9 items-center rounded-pill bg-surface-strong px-sm text-sm font-semibold text-ink hover:bg-hairline md:flex md:px-base"
                >
                  마이페이지
                </Link>
                <button
                  type="button"
                  onClick={handleLogout}
                  disabled={isLoggingOut}
                  aria-label={isLoggingOut ? '로그아웃 중' : '로그아웃'}
                  className="hidden h-9 w-9 items-center justify-center rounded-pill text-sm font-semibold text-muted hover:bg-surface-strong hover:text-ink disabled:cursor-not-allowed disabled:opacity-60 md:flex md:w-auto md:px-sm"
                >
                  <LogOut size={18} aria-hidden className="sm:hidden" />
                  <span className="hidden sm:inline">
                    {isLoggingOut ? '로그아웃 중…' : '로그아웃'}
                  </span>
                </button>
              </>
            ) : (
              <Link
                to="/login"
                className="flex h-9 items-center rounded-pill bg-surface-strong px-base text-sm font-semibold text-ink hover:bg-hairline"
              >
                로그인
              </Link>
            )}
            <button
              type="button"
              aria-label="메뉴 열기"
              aria-expanded={isMenuOpen}
              onClick={() => setIsMenuOpen((open) => !open)}
              className="rounded-full p-2 text-body hover:bg-surface-strong hover:text-ink md:hidden"
            >
              <Menu size={22} />
            </button>
          </div>
        </div>
      </div>

      <div
        className={`absolute left-0 right-0 top-full z-30 overflow-hidden border-b border-hairline bg-canvas transition-all duration-250 ease-out ${
          isMenuOpen
            ? 'pointer-events-auto max-h-96 opacity-100 translate-y-0'
            : 'pointer-events-none max-h-0 opacity-0 -translate-y-1'
        }`}
      >
        <div className="mx-auto flex w-full max-w-[1200px] flex-col gap-sm px-base pb-base pt-sm sm:px-lg">
          {/* 링크 사이에 문장으로 끼워 두면 누를 수 있는 항목처럼 보여, 마이페이지 보증금 카드와 같은 타일로 맨 위에 올린다. */}
          {isAuthenticated === true && availableDeposit !== null && (
            <div className="flex items-center justify-between rounded-lg bg-surface-soft px-base py-sm">
              <span className="text-sm text-body">{AVAILABLE_DEPOSIT_LABEL}</span>
              <span className="text-base font-bold text-ink tabular-nums">{formatWon(availableDeposit)}</span>
            </div>
          )}
          <p className="px-sm text-xs font-semibold uppercase tracking-[0.08em] text-body">메뉴</p>
          {NAV_LINKS.filter((link) => !link.authenticatedOnly || isAuthenticated === true).map((link) => {
            const isActive = location.pathname === link.pathname
            return (
              <Link
                key={link.to}
                to={link.to}
                onClick={() => setIsMenuOpen(false)}
                aria-current={isActive ? 'page' : undefined}
                className={`rounded-lg px-sm py-3 text-base font-medium transition-colors ${
                  isActive ? 'bg-surface-soft text-primary' : 'text-ink hover:bg-surface-soft'
                }`}
              >
                {link.label}
              </Link>
            )
          })}
          {isAuthenticated === true && (
            <>
              <Link
                to="/mypage"
                onClick={() => setIsMenuOpen(false)}
                className="rounded-lg px-sm py-3 text-base font-medium text-ink hover:bg-surface-soft"
              >
                마이페이지
              </Link>
              <button
                type="button"
                onClick={() => {
                  setIsMenuOpen(false)
                  handleLogout()
                }}
                disabled={isLoggingOut}
                aria-label={isLoggingOut ? '로그아웃 중' : '로그아웃'}
                className="flex items-center gap-sm rounded-lg px-sm py-3 text-base font-medium text-ink hover:bg-surface-soft disabled:cursor-not-allowed disabled:opacity-60"
              >
                <LogOut size={18} aria-hidden />
                <span>{isLoggingOut ? '로그아웃 중…' : '로그아웃'}</span>
              </button>
            </>
          )}
        </div>
      </div>

      <Modal
        isOpen={isSearchOpen}
        onClose={() => setIsSearchOpen(false)}
        title="경매 검색"
      >
        <form onSubmit={submitSearch} role="search" className="flex flex-col gap-base">
          {searchField}
          <Button type="submit" size="md">검색하기</Button>
        </form>
      </Modal>
    </header>
  )
}

export default TopNav
