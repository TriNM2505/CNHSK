import React from 'react'
import ReactDOM from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'

import { appRoutes } from './app/routes'
import './styles/index.css'

const root = document.getElementById('root')
if (!root) throw new Error('Application root is missing.')

const router = createBrowserRouter(appRoutes)

ReactDOM.createRoot(root).render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>,
)
