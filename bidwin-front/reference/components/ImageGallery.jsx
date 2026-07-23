import { useState } from 'react'
import { Headphones } from 'lucide-react'
import styles from './ImageGallery.module.css'

const SLOTS = [0, 1, 2, 3]

export default function ImageGallery() {
  const [active, setActive] = useState(0)

  return (
    <div className={styles.wrap}>
      <div className={styles.thumbs}>
        {SLOTS.map((i) => (
          <button
            key={i}
            className={`${styles.thumb} ${active === i ? styles.thumbActive : ''}`}
            onClick={() => setActive(i)}
            aria-label={`이미지 ${i + 1}`}
          >
            <Headphones size={20} strokeWidth={1.6} />
          </button>
        ))}
      </div>
      <div className={styles.main}>
        <div className={styles.mainGlow} />
        <Headphones size={96} strokeWidth={1.1} className={styles.mainIcon} />
        <span className={styles.mainLabel}>상품 이미지 {active + 1} / {SLOTS.length}</span>
      </div>
    </div>
  )
}
