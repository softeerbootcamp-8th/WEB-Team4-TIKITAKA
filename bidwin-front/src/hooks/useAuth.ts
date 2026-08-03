import { useContext } from 'react'
import { AuthContext } from '../lib/auth/auth-context'

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === null) throw new Error('AuthProvider 안에서 useAuth를 사용해야 합니다.')
  return context
}
