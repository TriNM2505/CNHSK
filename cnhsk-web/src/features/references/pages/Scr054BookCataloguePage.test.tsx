import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { listReferences } from '../api/references'
import { bookPage, bookReference } from '../test-fixtures'
import Scr054BookCataloguePage from './Scr054BookCataloguePage'

vi.mock('@/shared/auth', () => ({ useCurrentUser: () => ({ user: null }) }))
vi.mock('../api/references', () => ({ listReferences: vi.fn(), reportReference: vi.fn() }))
const list = vi.mocked(listReferences)

describe('SCR-054 book catalogue', () => {
  beforeEach(() => {
    list.mockReset().mockResolvedValue(bookPage)
  })
  it('always requests BOOK and links to detail without external purchase buttons', async () => {
    render(
      <MemoryRouter initialEntries={['/references/books?kind=PODCAST']}>
        <Scr054BookCataloguePage />
      </MemoryRouter>,
    )
    expect(await screen.findByRole('link', { name: bookReference.title })).toHaveAttribute(
      'href',
      '/references/books/503',
    )
    expect(list.mock.lastCall?.[0].kind).toBe('BOOK')
    expect(screen.getByRole('link', { name: 'Xem chi tiết' })).toHaveAttribute(
      'href',
      '/references/books/503',
    )
    expect(screen.getByText(bookReference.author!)).toBeVisible()
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Mở' })).not.toBeInTheDocument()
  })
  it('filters HSK and exact tag, then navigates pages', async () => {
    const event = userEvent.setup()
    list.mockResolvedValue({ ...bookPage, total: 40 })
    render(
      <MemoryRouter>
        <Scr054BookCataloguePage />
      </MemoryRouter>,
    )
    await screen.findByText(bookReference.title)
    await event.selectOptions(screen.getByLabelText('Cấp HSK'), '3')
    await event.type(screen.getByLabelText('Tag'), ' đọc ')
    await event.click(screen.getByRole('button', { name: 'Áp dụng' }))
    await waitFor(() =>
      expect(list.mock.lastCall?.[0]).toMatchObject({
        kind: 'BOOK',
        hsk: 3,
        tag: ' đọc ',
        page: 0,
      }),
    )
    await event.click(screen.getByRole('button', { name: 'Sau' }))
    await waitFor(() =>
      expect(list.mock.lastCall?.[0]).toMatchObject({
        kind: 'BOOK',
        hsk: 3,
        tag: ' đọc ',
        page: 1,
      }),
    )
  })
  it('shows empty state for an empty API page', async () => {
    list.mockResolvedValue({ ...bookPage, items: [], total: 0 })
    render(
      <MemoryRouter>
        <Scr054BookCataloguePage />
      </MemoryRouter>,
    )
    expect(await screen.findByText('Chưa có tài liệu phù hợp.')).toBeVisible()
  })
})
