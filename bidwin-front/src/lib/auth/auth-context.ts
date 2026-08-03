import { createContext } from 'react'

interface AuthContextValue {
  isAuthenticated: boolean | null
  setAuthenticated: (authenticated: boolean) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
