import { CheckCircle2, Clock } from 'lucide-react'
import { useCallback, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'
import type { ToastTone } from './toast-context'
import { ToastContext } from './toast-context'

interface ToastItem {
  id: number
  message: string
  tone: ToastTone
}

const TOAST_DURATION_MS = 2400

function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastItem | null>(null)
  const nextId = useRef(0)

  const showToast = useCallback((message: string, tone: ToastTone = 'success') => {
    const id = nextId.current++
    setToast({ id, message, tone })
    setTimeout(() => {
      setToast((current) => (current?.id === id ? null : current))
    }, TOAST_DURATION_MS)
  }, [])

  const value = useMemo(() => ({ showToast }), [showToast])

  return (
    <ToastContext.Provider value={value}>
      {children}
      {toast &&
        createPortal(
          <div className="pointer-events-none fixed inset-x-0 bottom-24 z-50 flex justify-center">
            <div className="flex items-center gap-2 rounded-pill bg-ink px-lg py-sm text-sm font-semibold text-on-dark shadow-card">
              {toast.tone === 'info' ? (
                <Clock size={16} className="text-accent-yellow" />
              ) : (
                <CheckCircle2 size={16} className="text-up" />
              )}
              <span>{toast.message}</span>
            </div>
          </div>,
          document.body,
        )}
    </ToastContext.Provider>
  )
}

export default ToastProvider
