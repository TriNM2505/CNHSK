import type { RouteObject } from 'react-router-dom'

import App from './App'
import GameHubPage from './pages/GameHubPage'
import GameSessionPage from './pages/GameSessionPage'
import NotFoundPage from './pages/NotFoundPage'

export const appRoutes = [
  {
    element: <App />,
    children: [
      { index: true, element: <GameHubPage /> },
      { path: 'play/:code', element: <GameSessionPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
] satisfies RouteObject[]
