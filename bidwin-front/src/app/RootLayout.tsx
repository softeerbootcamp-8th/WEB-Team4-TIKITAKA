import { Outlet } from 'react-router-dom'
import ToastProvider from '../components/feedback/ToastProvider'
import TopNav from '../components/layout/TopNav'
import AuthProvider from '../lib/auth/AuthProvider'

function RootLayout() {
  return (
    <AuthProvider>
      <ToastProvider>
        <div className="min-h-dvh bg-canvas">
          <TopNav />
          <Outlet />
        </div>
      </ToastProvider>
    </AuthProvider>
  )
}

export default RootLayout
