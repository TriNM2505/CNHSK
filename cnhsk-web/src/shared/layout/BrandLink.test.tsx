import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import type { CurrentUser, RoleCode } from '@/features/auth/types'
import { AuthContext } from '@/shared/auth'

import { BrandLink } from './AppShell'

function renderLogo(roles: RoleCode[] | null) {
  const user: CurrentUser | null = roles
    ? { id: 3, roles, displayName: 'Mai', email: 'mai@example.test', emailVerified: true }
    : null
  return render(
    <MemoryRouter>
      <AuthContext.Provider
        value={{
          error: null,
          reloadCurrentUser: vi.fn(),
          setAuthenticatedUser: vi.fn(),
          signOut: vi.fn(),
          status: user ? 'authenticated' : 'guest',
          user,
        }}
      >
        <BrandLink />
      </AuthContext.Provider>
    </MemoryRouter>,
  )
}

describe('brand logo', () => {
  it.each<RoleCode[]>([['TEACHER'], ['CONTENT_EDITOR'], ['MODERATOR'], ['ADMIN']])(
    'is a plain mark for staff %s, never a link to the landing page',
    (role) => {
      renderLogo([role])
      expect(screen.queryByRole('link')).not.toBeInTheDocument()
      expect(screen.getByTestId('brand-mark')).toBeVisible()
    },
  )

  it.each([[['LEARNER']], [['LEARNER', 'TEACHER']], [null]] as [RoleCode[] | null][])(
    'links learners and guests home (%s)',
    (roles) => {
      renderLogo(roles)
      expect(screen.getByRole('link', { name: 'CNHSK - Trang chủ' })).toHaveAttribute('href', '/')
    },
  )
})
