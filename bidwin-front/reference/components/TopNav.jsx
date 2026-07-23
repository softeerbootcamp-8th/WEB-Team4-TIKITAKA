import { Search, Gavel } from 'lucide-react'
import styles from './TopNav.module.css'

export default function TopNav() {
  return (
    <header className={styles.nav}>
      <div className={styles.inner}>
        <a className={styles.brand} href="#top">
          <Gavel size={20} strokeWidth={2.25} />
          급처마켓
        </a>
        <nav className={styles.links}>
          <a href="#top">진행중 경매</a>
          <a href="#top">나의 입찰</a>
          <a href="#top">판매하기</a>
        </nav>
        <div className={styles.actions}>
          <button className={styles.iconBtn} aria-label="검색">
            <Search size={18} />
          </button>
          <button className={styles.signIn}>로그인</button>
        </div>
      </div>
    </header>
  )
}
