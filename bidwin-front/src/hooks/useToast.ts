import { useContext } from 'react'
import { ToastContext } from '../components/feedback/toast-context'

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast는 ToastProvider 안에서만 사용할 수 있어요.')
  return ctx
}
