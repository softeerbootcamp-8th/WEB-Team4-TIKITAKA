import { CheckCircle2, Clock } from 'lucide-react'
import styles from './Toast.module.css'

export default function Toast({ toast }) {
  if (!toast) return null
  const Icon = toast.tone === 'info' ? Clock : CheckCircle2

  return (
    <div className={styles.wrap} key={toast.key}>
      <div className={`${styles.toast} ${toast.tone === 'info' ? styles.toneInfo : styles.toneSuccess}`}>
        <Icon size={16} />
        <span>{toast.message}</span>
      </div>
    </div>
  )
}
