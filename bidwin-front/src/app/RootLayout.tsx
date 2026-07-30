import { Outlet } from 'react-router-dom'

function RootLayout() {
  return (
    <div className="min-h-dvh bg-canvas">
      <Outlet />
    </div>
  )
}

export default RootLayout
