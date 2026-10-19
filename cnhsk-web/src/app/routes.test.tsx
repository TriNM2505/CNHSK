import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { appRoutes } from '@/app/routes'

function renderRoute(path: string) {
  const memoryRouter = createMemoryRouter(appRoutes, { initialEntries: [path] })

  return render(<RouterProvider router={memoryRouter} />)
}

describe('application routes', () => {
  it('renders the temporary home page at the root route', () => {
    renderRoute('/')

    expect(screen.getByRole('heading', { name: 'CNHSK' })).toBeVisible()
    expect(screen.getByText('汉字')).toHaveAttribute('lang', 'zh-CN')
  })

  it('renders the not-found page for an unknown route', () => {
    renderRoute('/khong-ton-tai')

    expect(screen.getByRole('heading', { name: 'Không tìm thấy trang' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Về trang chủ' })).toHaveAttribute('href', '/')
  })
})
