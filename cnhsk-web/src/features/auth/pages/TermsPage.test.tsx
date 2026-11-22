import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { appRouteDefinitions, canUserAccessRoute } from '@/app/routeAccess'

import TermsPage from './TermsPage'

describe('TermsPage', () => {
  it('lists the terms and the data policy with continuous numbering and a contact', () => {
    render(<TermsPage />)

    expect(
      screen.getByRole('heading', { level: 1, name: 'Điều khoản sử dụng và chính sách dữ liệu' }),
    ).toBeVisible()
    expect(screen.getByRole('heading', { name: '8. Khoá tài khoản' })).toBeVisible()
    expect(screen.getByRole('heading', { name: '9. Dữ liệu chúng tôi lưu' })).toBeVisible()
    expect(screen.getByRole('heading', { name: '15. Thay đổi' })).toBeVisible()
    expect(screen.getByText(/info@hoc-ba\.edu\.vn/)).toBeVisible()
  })

  it('is open to everyone', () => {
    const route = appRouteDefinitions.find(({ path }) => path === '/terms')
    expect(route).toMatchObject({ access: 'public', layout: 'public' })
    expect(canUserAccessRoute({} as never, 'public')).toBe(true)
  })
})
