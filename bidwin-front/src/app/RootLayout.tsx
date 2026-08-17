import { Outlet } from 'react-router-dom'
import ToastProvider from '../components/feedback/ToastProvider'
import TopNav from '../components/layout/TopNav'
import AuthProvider from '../lib/auth/AuthProvider'
import ServerClockProvider from '../lib/clock/ServerClockProvider'

function RootLayout() {
  return (
    <AuthProvider>
      <ServerClockProvider>
        <ToastProvider>
          <div className="min-h-dvh bg-canvas">
            <TopNav />
            <Outlet />
          </div>
        </ToastProvider>
      </ServerClockProvider>
    </AuthProvider>
  )
}

export default RootLayout
