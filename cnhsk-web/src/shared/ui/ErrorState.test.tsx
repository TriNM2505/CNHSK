import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { ErrorState } from '@/shared/ui/ErrorState'

describe('ErrorState', () => {
  it('shows a request id and retries when the action is available', async () => {
    const user = userEvent.setup()
    const onRetry = vi.fn()
    render(
      <ErrorState
        message="Kết nối tới máy chủ bị gián đoạn."
        onRetry={onRetry}
        requestId="req-mau-123"
      />,
    )

    expect(screen.getByText('Mã lỗi: req-mau-123')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(onRetry).toHaveBeenCalledOnce()
  })

  it('omits retry and request id when they are unavailable', () => {
    render(<ErrorState message="Không thể hoàn thành thao tác." />)

    expect(screen.queryByRole('button', { name: 'Thử lại' })).not.toBeInTheDocument()
    expect(screen.queryByText(/Mã lỗi:/)).not.toBeInTheDocument()
  })
})
