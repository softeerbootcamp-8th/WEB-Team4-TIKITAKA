import { Outlet } from 'react-router-dom'
import ToastProvider from '../components/feedback/ToastProvider'
import TopNav from '../components/layout/TopNav'

function RootLayout() {
  return (
    <ToastProvider>
      <div className="min-h-dvh bg-canvas">
        <TopNav />
        <Outlet />
      </div>
    </ToastProvider>
  )
}

export default RootLayout
