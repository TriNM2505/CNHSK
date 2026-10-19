import { act, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { Skeleton } from '@/shared/ui/Skeleton'

describe('Skeleton', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('appears only after 300 milliseconds', () => {
    vi.useFakeTimers()
    render(<Skeleton variant="table" />)

    expect(screen.queryByRole('status', { name: 'Đang tải' })).not.toBeInTheDocument()
    act(() => vi.advanceTimersByTime(299))
    expect(screen.queryByRole('status', { name: 'Đang tải' })).not.toBeInTheDocument()
    act(() => vi.advanceTimersByTime(1))
    expect(screen.getByRole('status', { name: 'Đang tải' })).toBeVisible()
  })

  it.each(['text', 'card', 'table'] as const)('renders the %s variant', (variant) => {
    vi.useFakeTimers()
    render(<Skeleton variant={variant} />)
    act(() => vi.advanceTimersByTime(300))

    expect(screen.getByRole('status', { name: 'Đang tải' })).toBeVisible()
  })
})
