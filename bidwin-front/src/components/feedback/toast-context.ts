import { createContext } from 'react'

export type ToastTone = 'success' | 'info'

export interface ToastContextValue {
  showToast: (message: string, tone?: ToastTone) => void
}

export const ToastContext = createContext<ToastContextValue | null>(null)
