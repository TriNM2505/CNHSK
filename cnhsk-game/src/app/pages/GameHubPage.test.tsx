import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'

import GameHubPage from './GameHubPage'

const names = ['Mưa chữ', 'Ghép Pinyin', 'Ghép bộ thủ', 'Bắt chữ', 'Thần bút', 'Phi đao']
const codes = [
  'CHAR_RAIN',
  'PINYIN_MATCH',
  'RADICAL_MATCH',
  'CHAR_CATCH',
  'BRUSH_STROKE',
  'FLYING_DART',
]
const games = names.map((name, index) => ({
  code: codes[index],
  name,
  description: 'Mô tả fixture ' + name,
  skill: 'CHARACTER',
  howToPlay: 'Chọn mục phù hợp ' + name,
  timeLimitSeconds: 30,
  playable: index !== 1,
  myStats: index === 0 ? { bestPoints: 0, roundsPlayed: 0 } : null,
}))

function renderHub(...responses: Response[]) {
  const fetchMock = vi.fn()
  responses.forEach((response) => fetchMock.mockResolvedValueOnce(response))
  vi.stubGlobal('fetch', fetchMock)
  render(
    <MemoryRouter>
      <GameHubPage />
    </MemoryRouter>,
  )
  return fetchMock
}

afterEach(() => vi.unstubAllGlobals())

it('shows six cards, zero personal stats and a disabled game', async () => {
  const fetch = renderHub(Response.json(games))
  expect(screen.getByRole('status')).toHaveTextContent('Đang tải')
  const catalog = await screen.findByRole('list', { name: 'Các trò chơi' })
  expect(within(catalog).getAllByRole('listitem')).toHaveLength(6)
  expect(
    within(catalog)
      .getAllByRole('heading')
      .map((heading) => heading.textContent),
  ).toEqual(names)
  expect(screen.getByText('Mô tả fixture Mưa chữ')).toBeVisible()
  expect(screen.getAllByText('Chữ Hán · 30 giây')).toHaveLength(6)
  const rain = screen.getByRole('link', { name: 'Chơi Mưa chữ' }).closest('article')
  if (!rain) throw new Error('Missing game card')
  expect(within(rain).getByText('Điểm cao nhất')).toBeVisible()
  expect(within(rain).getAllByText('0')).toHaveLength(2)
  expect(screen.getAllByText('Điểm cao nhất')).toHaveLength(1)
  expect(screen.getByRole('button', { name: 'Chơi Ghép Pinyin' })).toBeDisabled()
  expect(screen.getByText('Chưa đủ dữ liệu')).toBeVisible()
  expect(screen.queryByRole('link', { name: /bảng xếp hạng/i })).not.toBeInTheDocument()
  expect(fetch).toHaveBeenCalledWith(
    '/api/community/games',
    expect.objectContaining({ credentials: 'include' }),
  )
})

it('expands instructions and only creates links for the default or HSK 1–9', async () => {
  const user = userEvent.setup()
  renderHub(Response.json(games))
  const play = await screen.findByRole('link', { name: 'Chơi Mưa chữ' })
    expect(play).toHaveAttribute('href', '/play/char-rain')
  const select = screen.getByRole('combobox', { name: 'Cấp HSK' })
  expect(
    within(select)
      .getAllByRole('option')
      .map((option) => option.getAttribute('value')),
  ).toEqual(['', '1', '2', '3', '4', '5', '6', '7', '8', '9'])
  await user.selectOptions(select, '9')
    expect(play).toHaveAttribute('href', '/play/char-rain?hskLevel=9')
  await user.selectOptions(select, '')
    expect(play).toHaveAttribute('href', '/play/char-rain')
  const summary = screen.getAllByText('Cách chơi')[0]
  if (!summary) throw new Error('Missing game instructions')
  await user.click(summary)
  expect(summary.closest('details')).toHaveAttribute('open')
})

it('offers retry after an error and shows an empty catalog', async () => {
  const user = userEvent.setup()
  const fetch = renderHub(
    Response.json({ error_code: 'INTERNAL_ERROR' }, { status: 500 }),
    Response.json([]),
  )
  expect(await screen.findByRole('alert')).toHaveTextContent('Chưa tải được')
  await user.click(screen.getByRole('button', { name: 'Thử lại' }))
  expect(await screen.findByText('Chưa có trò chơi.')).toBeVisible()
  expect(fetch).toHaveBeenCalledTimes(2)
})
