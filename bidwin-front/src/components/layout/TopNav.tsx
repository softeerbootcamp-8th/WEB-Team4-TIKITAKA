import { Gavel, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, createSearchParams, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import Button from '../ui/Button'
import Modal from '../ui/Modal'

const NAV_LINKS = [
  { label: '진행중 경매', to: '/auctions' },
  { label: '판매하기', to: '/auctions/new' },
]

function TopNav() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const currentKeyword = location.pathname === '/auctions'
    ? new URLSearchParams(location.search).get('q') ?? ''
    : ''
  const [keyword, setKeyword] = useState(currentKeyword)
  const [isSearchOpen, setIsSearchOpen] = useState(false)

  useEffect(() => setKeyword(currentKeyword), [currentKeyword])

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const normalized = keyword.trim()
    navigate({
      pathname: '/auctions',
      search: normalized ? `?${createSearchParams({ q: normalized })}` : '',
    })
    setIsSearchOpen(false)
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
    <header className="sticky top-0 z-40 h-16 border-b border-hairline bg-canvas">
      <div className="mx-auto flex h-full max-w-[1200px] items-center gap-xl px-lg">
        <Link to="/" className="flex items-center gap-xs font-bold text-ink">
          <Gavel size={20} className="text-primary" />
          급처마켓
        </Link>
        <nav className="hidden flex-1 gap-lg lg:flex">
          {NAV_LINKS.map((link) => (
            <Link
              key={link.to}
              to={link.to}
              className="text-sm font-medium text-body hover:text-ink"
            >
              {link.label}
            </Link>
          ))}
        </nav>
        <form onSubmit={submitSearch} role="search" className="hidden w-[min(280px,30vw)] md:flex">
          {searchField}
        </form>
        <div className="flex items-center gap-xs">
          <button
            type="button"
            aria-label="검색"
            onClick={() => setIsSearchOpen(true)}
            className="flex h-9 w-9 items-center justify-center rounded-full text-body hover:bg-surface-strong hover:text-ink md:hidden"
          >
            <Search size={18} />
          </button>
          <Link
            to={isAuthenticated === true ? '/mypage' : '/login'}
            className="flex h-9 items-center rounded-pill bg-surface-strong px-base text-sm font-semibold text-ink hover:bg-hairline"
          >
            {isAuthenticated === true ? '마이페이지' : '로그인'}
          </Link>
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
