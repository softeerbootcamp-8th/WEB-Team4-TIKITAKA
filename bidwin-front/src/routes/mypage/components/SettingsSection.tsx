import { ChevronRight, Gavel, ShoppingBag, Trophy, UserCog } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { Link } from 'react-router-dom'
import { HISTORY_TAB, SETTINGS_TEXT, historyPath } from '../constants'

const ROW_ICON_SIZE = 18
const CHEVRON_SIZE = 16

const SECTION_SURFACE_CLASS = 'rounded-xl border border-hairline-soft bg-canvas p-lg'
const ROW_CLASS =
  'flex w-full items-center gap-sm rounded-md px-sm py-sm text-left text-sm font-medium text-body transition-colors hover:bg-surface-soft hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary'

const HISTORY_LINKS: { label: string; icon: LucideIcon; path: string }[] = [
  { label: SETTINGS_TEXT.purchase, icon: ShoppingBag, path: historyPath(HISTORY_TAB.purchase) },
  { label: SETTINGS_TEXT.bidding, icon: Gavel, path: historyPath(HISTORY_TAB.bidding) },
  { label: SETTINGS_TEXT.won, icon: Trophy, path: historyPath(HISTORY_TAB.won) },
]

/*
 * 설정 목록. 내역 세 개는 내역 페이지의 해당 탭으로 넘기고,
 * 내 정보 관리만 페이지 이동 없이 오른쪽 드로어를 연다.
 */
function SettingsSection({ onOpenMyInfo }: { onOpenMyInfo: () => void }) {
  return (
    <section className={SECTION_SURFACE_CLASS}>
      <h2 className="text-base font-bold text-ink">{SETTINGS_TEXT.title}</h2>

      <ul className="mt-xs grid grid-cols-1 gap-x-lg sm:grid-cols-2">
        {HISTORY_LINKS.map(({ label, icon: Icon, path }) => (
          <li key={path}>
            <Link to={path} className={ROW_CLASS}>
              <Icon size={ROW_ICON_SIZE} className="shrink-0 text-muted" />
              <span className="flex-1">{label}</span>
              <ChevronRight size={CHEVRON_SIZE} className="shrink-0 text-muted-soft" />
            </Link>
          </li>
        ))}

        <li>
          <button type="button" onClick={onOpenMyInfo} className={ROW_CLASS}>
            <UserCog size={ROW_ICON_SIZE} className="shrink-0 text-muted" />
            <span className="flex-1">{SETTINGS_TEXT.myInfo}</span>
            <ChevronRight size={CHEVRON_SIZE} className="shrink-0 text-muted-soft" />
          </button>
        </li>
      </ul>
    </section>
  )
}

export default SettingsSection
