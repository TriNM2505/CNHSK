import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { Pagination } from '@/shared/ui/Pagination'

describe('Pagination', () => {
  it('announces the current page and prevents moving before the first page', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<Pagination onChange={onChange} page={1} size={10} total={31} />)

    expect(screen.getByText('Trang 1 / 4')).toBeVisible()
    expect(screen.getByRole('button', { name: /Trước/ })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: /Sau/ }))
    expect(onChange).toHaveBeenCalledWith(2)
  })

  it('prevents moving after the last page', () => {
    render(<Pagination onChange={vi.fn()} page={4} size={10} total={31} />)

    expect(screen.getByRole('button', { name: /Sau/ })).toBeDisabled()
  })
})
