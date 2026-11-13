import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import { listReferences, reportReference } from '../api/references'
import { mediaPage, mediaReference, podcastReference } from '../test-fixtures'
import type { PublicReferencePage } from '../types'
import Scr053MediaReferencesPage from './Scr053MediaReferencesPage'

const auth = vi.hoisted(() => ({ user: null as null | { id: number; roles: string[] } }))
vi.mock('@/shared/auth', () => ({ useCurrentUser: () => auth }))
vi.mock('../api/references', () => ({ listReferences: vi.fn(), reportReference: vi.fn() }))
const list = vi.mocked(listReferences)
const report = vi.mocked(reportReference)

function renderPage(url = '/references/media') {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Scr053MediaReferencesPage />
    </MemoryRouter>,
  )
}

describe('SCR-053 media references', () => {
  beforeEach(() => {
    auth.user = null
    list.mockReset().mockResolvedValue(mediaPage)
    report.mockReset().mockResolvedValue(undefined)
  })
  it('loads YouTube by default and renders safe external links, nulls and tags', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { name: mediaReference.title })).toBeVisible()
    expect(list).toHaveBeenCalledWith(
      { kind: 'YOUTUBE_CHANNEL', hsk: undefined, tag: undefined, page: 0 },
      expect.any(AbortSignal),
    )
    expect(screen.getByRole('link', { name: 'Mở' })).toHaveAttribute('target', '_blank')
    expect(screen.getByRole('link', { name: 'Mở' })).toHaveAttribute('rel', 'noopener noreferrer')
    expect(screen.getByRole('link', { name: 'Mở' })).toHaveAttribute('href', mediaReference.url)
    expect(screen.getByText('hội thoại')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Báo lỗi' })).not.toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })
  it('switches tabs, resets page and filters only HSK 1–3', async () => {
    const event = userEvent.setup()
    list.mockResolvedValue({ ...mediaPage, page: 2, total: 60 })
    renderPage('/references/media?page=2')
    await screen.findByText(mediaReference.title)
    list.mockResolvedValue({ ...mediaPage, items: [podcastReference] })
    await event.click(screen.getByRole('tab', { name: 'Podcast' }))
    await screen.findByText(podcastReference.title)
    expect(list.mock.lastCall?.[0]).toMatchObject({ kind: 'PODCAST', page: 0 })
    const levels = screen.getByLabelText('Cấp HSK')
    expect(
      within(levels)
        .getAllByRole('option')
        .map((option) => option.textContent),
    ).toEqual(['Mọi cấp', 'HSK 1', 'HSK 2', 'HSK 3'])
    await event.selectOptions(levels, '2')
    await waitFor(() =>
      expect(list.mock.lastCall?.[0]).toMatchObject({ kind: 'PODCAST', hsk: 2, page: 0 }),
    )
    expect(screen.getByText('Mọi cấp HSK')).toBeVisible()
  })
  it('applies the exact tag only on submit and resets pagination', async () => {
    const event = userEvent.setup()
    renderPage('/references/media?page=2')
    await screen.findByText(mediaReference.title)
    const calls = list.mock.calls.length
    await event.type(screen.getByLabelText('Tag'), ' nghe & đọc ')
    expect(list).toHaveBeenCalledTimes(calls)
    await event.click(screen.getByRole('button', { name: 'Áp dụng' }))
    await waitFor(() =>
      expect(list.mock.lastCall?.[0]).toMatchObject({ tag: ' nghe & đọc ', page: 0 }),
    )
  })
  it('uses zero-based API pages and preserves filters', async () => {
    const event = userEvent.setup()
    list.mockResolvedValue({ ...mediaPage, total: 40 })
    renderPage('/references/media?kind=PODCAST&hsk=2&tag=nghe')
    await screen.findByText(mediaReference.title)
    await event.click(screen.getByRole('button', { name: 'Sau' }))
    await waitFor(() =>
      expect(list.mock.lastCall?.[0]).toMatchObject({
        kind: 'PODCAST',
        hsk: 2,
        tag: 'nghe',
        page: 1,
      }),
    )
  })
  it('cancels an old request and never lets its result replace the current tab', async () => {
    const event = userEvent.setup()
    let resolveOld!: (value: PublicReferencePage) => void
    list.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveOld = resolve
      }),
    )
    list.mockResolvedValueOnce({ ...mediaPage, items: [podcastReference] })
    renderPage()
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải tài liệu')
    const oldSignal = list.mock.calls[0]![1]
    await event.click(screen.getByRole('tab', { name: 'Podcast' }))
    await screen.findByText(podcastReference.title)
    expect(oldSignal?.aborted).toBe(true)
    await act(async () => resolveOld(mediaPage))
    expect(screen.queryByText(mediaReference.title)).not.toBeInTheDocument()
  })
  it('distinguishes loading, network error with retry, and genuinely empty', async () => {
    const event = userEvent.setup()
    list
      .mockRejectedValueOnce(
        new ApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Mất kết nối.' }),
      )
      .mockResolvedValueOnce({ ...mediaPage, items: [], total: 0 })
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Mất kết nối.')
    expect(screen.queryByText('Chưa có tài liệu phù hợp.')).not.toBeInTheDocument()
    await event.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Chưa có tài liệu phù hợp.')).toBeVisible()
  })
  it('renders hostile description as text (AT-17)', async () => {
    const text = '<script>window.bad=true</script><img src=x onerror=alert(1)>'
    list.mockResolvedValue({ ...mediaPage, items: [{ ...mediaReference, description: text }] })
    const { container } = renderPage()
    expect(await screen.findByText(text)).toBeVisible()
    expect(container.querySelector('script, img[onerror]')).toBeNull()
  })
  it.each(['ADMIN', 'CONTENT_EDITOR', 'TEACHER', 'MODERATOR', 'ACCOUNTANT'])(
    'does not offer report to %s',
    async (role) => {
      auth.user = { id: 1, roles: [role] }
      renderPage()
      await screen.findByText(mediaReference.title)
      expect(screen.queryByText('Tuỳ chọn')).not.toBeInTheDocument()
    },
  )
  it('reports only as learner, prevents duplicate sends and shows 204 success', async () => {
    const event = userEvent.setup()
    auth.user = { id: 1, roles: ['LEARNER'] }
    let finish!: () => void
    report.mockReturnValue(
      new Promise((resolve) => {
        finish = resolve
      }),
    )
    renderPage()
    await screen.findByText(mediaReference.title)
    await event.click(screen.getByText('Tuỳ chọn'))
    await event.click(screen.getByRole('button', { name: 'Báo lỗi' }))
    await event.click(screen.getByRole('button', { name: 'Gửi báo lỗi' }))
    expect(screen.getByRole('button', { name: 'Gửi báo lỗi' })).toBeDisabled()
    expect(report).toHaveBeenCalledOnce()
    expect(report).toHaveBeenCalledWith(501, { reason: 'DEAD_LINK', note: null })
    await act(async () => finish())
    expect(await screen.findByText('Đã ghi nhận báo lỗi.')).toBeVisible()
  })
  it('reports rate limiting without converting it to success', async () => {
    const event = userEvent.setup()
    auth.user = { id: 1, roles: ['LEARNER'] }
    report.mockRejectedValue(
      new ApiError({ status: 429, errorCode: 'RATE_LIMITED', message: 'Hết lượt.' }),
    )
    renderPage()
    await screen.findByText(mediaReference.title)
    await event.click(screen.getByText('Tuỳ chọn'))
    await event.click(screen.getByRole('button', { name: 'Báo lỗi' }))
    await event.click(screen.getByRole('button', { name: 'Gửi báo lỗi' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Bạn đã báo lỗi tài liệu này hôm nay',
    )
    expect(screen.queryByText('Đã ghi nhận báo lỗi.')).not.toBeInTheDocument()
  })
  it('keeps an old report response out of a newly selected card', async () => {
    const event = userEvent.setup()
    auth.user = { id: 1, roles: ['LEARNER'] }
    list.mockResolvedValue({
      ...mediaPage,
      items: [mediaReference, { ...mediaReference, id: 504, title: 'Nguồn demo thứ hai' }],
      total: 2,
    })
    let finish!: () => void
    report.mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve
      }),
    )
    renderPage()
    await screen.findByText(mediaReference.title)
    await event.click(screen.getAllByText('Tuỳ chọn')[0]!)
    await event.click(screen.getAllByRole('button', { name: 'Báo lỗi' })[0]!)
    await event.click(screen.getByRole('button', { name: 'Gửi báo lỗi' }))
    await event.click(screen.getAllByText('Tuỳ chọn')[1]!)
    await event.click(screen.getAllByRole('button', { name: 'Báo lỗi' })[1]!)
    expect(screen.getByRole('button', { name: 'Gửi báo lỗi' })).toBeEnabled()
    await act(async () => finish())
    expect(screen.queryByText('Đã ghi nhận báo lỗi.')).not.toBeInTheDocument()
  })
})
