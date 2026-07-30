import { Gavel, Search } from 'lucide-react'
import { Link } from 'react-router-dom'

const NAV_LINKS = [
  { label: '진행중 경매', to: '/auctions' },
  { label: '판매하기', to: '/auctions/new' },
]

function TopNav() {
  return (
    <header className="sticky top-0 z-40 h-16 border-b border-hairline bg-canvas">
      <div className="mx-auto flex h-full max-w-[1200px] items-center gap-xl px-lg">
        <Link to="/" className="flex items-center gap-xs font-bold text-ink">
          <Gavel size={20} className="text-primary" />
          급처마켓
        </Link>
        <nav className="flex flex-1 gap-lg">
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
        <div className="flex items-center gap-xs">
          <button
            type="button"
            aria-label="검색"
            className="flex h-9 w-9 items-center justify-center rounded-full text-body hover:bg-surface-strong hover:text-ink"
          >
            <Search size={18} />
          </button>
          <Link
            to="/login"
            className="flex h-9 items-center rounded-pill bg-surface-strong px-base text-sm font-semibold text-ink hover:bg-hairline"
          >
            로그인
          </Link>
        </div>
      </div>
    </header>
  )
}

export default TopNav
