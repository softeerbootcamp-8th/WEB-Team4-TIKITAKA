import { useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { requestSession } from '../api/auth'
import { AuthContext } from './auth-context'

function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setAuthenticated] = useState<boolean | null>(null)

  useEffect(() => {
    let isMounted = true

    requestSession().then((result) => {
      if (isMounted && (result.ok || result.status === 401)) {
        setAuthenticated(result.ok)
      }
    })

    return () => {
      isMounted = false
    }
  }, [])

  const value = useMemo(
    () => ({ isAuthenticated, setAuthenticated }),
    [isAuthenticated],
  )

  return (
    <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
  )
}

export default AuthProvider
