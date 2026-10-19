import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { ScreenRoute } from '@/app/RouteScaffolding'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

vi.mock('@/app/screenRegistry', () => ({
  screenRegistry: {
    'SCR-001': () => <h1>Màn thật đã đăng ký</h1>,
  },
}))

const guestAuth: AuthContextValue = {
  error: null,
  reloadCurrentUser: vi.fn(async () => undefined),
  setAuthenticatedUser: vi.fn(),
  signOut: vi.fn(async () => undefined),
  status: 'guest',
  user: null,
}

describe('ScreenRoute', () => {
  it('renders a registered screen instead of the under-construction page', () => {
    render(
      <AuthContext.Provider value={guestAuth}>
        <MemoryRouter>
          <ScreenRoute access="public" layout="public" scr="SCR-001" />
        </MemoryRouter>
      </AuthContext.Provider>,
    )

    expect(screen.getByRole('heading', { name: 'Màn thật đã đăng ký' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Màn SCR-001 đang dựng' })).not.toBeInTheDocument()
  })
})
