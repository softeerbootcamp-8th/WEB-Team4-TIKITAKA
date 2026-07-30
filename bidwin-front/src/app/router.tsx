import { createBrowserRouter } from 'react-router-dom'
import NotFoundPage from './NotFoundPage'
import RootLayout from './RootLayout'
import { pageRoutes } from './routes'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    children: [...pageRoutes, { path: '*', element: <NotFoundPage /> }],
  },
])
