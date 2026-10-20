import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { Button } from '@/shared/ui/Button'
import { Toast, ToastProvider } from '@/shared/ui/Toast'
import { useToast } from '@/shared/ui/useToast'

function ToastTrigger() {
  const { showToast } = useToast()

  return (
    <Button onClick={() => showToast({ message: 'Đã lưu thay đổi', tone: 'success' })}>
      Hiện thông báo
    </Button>
  )
}

describe('Toast', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('uses status for success and hides itself after four seconds', () => {
    vi.useFakeTimers()
    const onClose = vi.fn()
    render(<Toast message="Đã hoàn thành" onClose={onClose} tone="success" />)

    expect(screen.getByRole('status')).toHaveTextContent('Đã hoàn thành')
    act(() => vi.advanceTimersByTime(3999))
    expect(onClose).not.toHaveBeenCalled()
    act(() => vi.advanceTimersByTime(1))
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('uses alert for errors and provides a close button', () => {
    const onClose = vi.fn()
    render(<Toast message="Không thể lưu" onClose={onClose} tone="error" />)

    expect(screen.getByRole('alert')).toHaveTextContent('Không thể lưu')
    fireEvent.click(screen.getByRole('button', { name: 'Đóng thông báo' }))
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('creates notifications through useToast inside ToastProvider', () => {
    render(
      <ToastProvider>
        <ToastTrigger />
      </ToastProvider>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Hiện thông báo' }))
    expect(screen.getByRole('status')).toHaveTextContent('Đã lưu thay đổi')
  })
})
