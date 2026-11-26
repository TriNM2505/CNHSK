import { act, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import type { Card } from '../types'
import { FlashcardViewer } from './FlashcardViewer'

const cards: Card[] = [
  { id: 1, front: '学校', back: 'trường học', reviewCount: 0, lapseCount: 0 },
  { id: 2, front: '老师', back: 'giáo viên', reviewCount: 0, lapseCount: 0 },
  { id: 3, front: '学生', back: 'học sinh', reviewCount: 0, lapseCount: 0 },
]

const card = () => screen.getByRole('button', { name: /^Thẻ \d+ trên 3/ })

afterEach(() => {
  vi.useRealTimers()
})

describe('flashcard browsing', () => {
  it('flips the big card and steps through the deck with buttons and arrow keys', async () => {
    render(<FlashcardViewer cards={cards} />)
    expect(screen.getByText('1 / 3')).toBeVisible()
    expect(card()).toHaveAttribute('data-flipped', 'false')
    await userEvent.click(card())
    expect(card()).toHaveAttribute('data-flipped', 'true')
    expect(card()).toHaveAccessibleName(/đang xem mặt sau/)

    await userEvent.click(screen.getByRole('button', { name: 'Thẻ sau' }))
    expect(screen.getByText('2 / 3')).toBeVisible()
    expect(card()).toHaveAttribute('data-flipped', 'false')
    fireEvent.keyDown(card(), { key: 'ArrowRight' })
    expect(screen.getByText('3 / 3')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Thẻ sau' })).toBeDisabled()
    fireEvent.keyDown(card(), { key: 'ArrowLeft' })
    expect(screen.getByText('2 / 3')).toBeVisible()
  })

  it('shuffles from the first card and restores deck order when shuffling is turned off', async () => {
    render(<FlashcardViewer cards={cards} />)
    await userEvent.click(screen.getByRole('button', { name: 'Thẻ sau' }))
    await userEvent.click(screen.getByRole('button', { name: 'Trộn thẻ' }))
    expect(screen.getByText('1 / 3')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Bỏ trộn thẻ' })).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(screen.getByRole('button', { name: 'Bỏ trộn thẻ' }))
    expect(card()).toHaveTextContent('学校')
  })

  it('auto-plays: shows the back, then moves on, and stops after the last card', () => {
    vi.useFakeTimers()
    render(<FlashcardViewer cards={cards} />)
    fireEvent.click(screen.getByRole('button', { name: 'Tự phát' }))
    act(() => vi.advanceTimersByTime(3000))
    expect(card()).toHaveAttribute('data-flipped', 'true')
    act(() => vi.advanceTimersByTime(3000))
    expect(screen.getByText('2 / 3')).toBeVisible()
    for (let step = 0; step < 4; step++) act(() => vi.advanceTimersByTime(3000))
    expect(screen.getByText('3 / 3')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Tự phát' })).toHaveAttribute('aria-pressed', 'false')
  })
})
