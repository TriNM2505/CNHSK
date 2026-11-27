import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser } from '@/features/auth/types'
import { getMyWallet } from '@/features/credits/api/wallet'
import { creditsFreeWallet, creditsWallet } from '@/features/credits/test-fixtures'
import { AuthContext } from '@/shared/auth'
import { LiveSessionContext } from '@/shared/auth/live-session'

import { AccountMenu } from './AccountMenu'

vi.mock('@/features/credits/api/wallet', () => ({ getMyWallet: vi.fn() }))
const wallet = vi.mocked(getMyWallet)

const learner: CurrentUser = {
  id: 7,
  roles: ['LEARNER'],
  displayName: 'Lan',
  email: 'lan@example.test',
  emailVerified: true,
}

function renderMenu(user: CurrentUser, live = true) {
  return render(
    <MemoryRouter>
      <LiveSessionContext.Provider value={live}>
        <AuthContext.Provider
          value={{
            error: null,
            reloadCurrentUser: vi.fn(),
            setAuthenticatedUser: vi.fn(),
            signOut: vi.fn(),
            status: 'authenticated',
            user,
          }}
        >
          <AccountMenu />
        </AuthContext.Provider>
      </LiveSessionContext.Provider>
    </MemoryRouter>,
  )
}

describe('premium mark on the account menu', () => {
  beforeEach(() => {
    wallet.mockReset()
  })

  it('crowns a learner whose PREMIUM is running and offers renewal', async () => {
    wallet.mockResolvedValue(creditsWallet)
    renderMenu(learner)
    expect(await screen.findByTestId('premium-crown')).toBeInTheDocument()
    expect(screen.getByLabelText('Tài khoản Lan, Premium')).toBeInTheDocument()
    expect(screen.getByText(/Premium đến 31\/12\/2026/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Gia hạn' })).toHaveAttribute(
      'href',
      '/account/credits',
    )
  })

  it('shows no crown on a free account', async () => {
    wallet.mockResolvedValue(creditsFreeWallet)
    renderMenu(learner)
    await waitFor(() => expect(wallet).toHaveBeenCalled())
    expect(screen.queryByTestId('premium-crown')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Nâng cấp' })).toBeInTheDocument()
  })

  it('never asks for a wallet outside a real session or for staff', () => {
    renderMenu(learner, false)
    renderMenu({ ...learner, id: 8, roles: ['CONTENT_EDITOR'] })
    expect(wallet).not.toHaveBeenCalled()
  })
})
