import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import { ReferenceReportForm } from './ReferenceReportForm'

describe('ReferenceReportForm', () => {
  it('sends selected reason and note without trimming', async () => {
    const event = userEvent.setup()
    const submit = vi.fn()
    render(
      <ReferenceReportForm pending={false} sent={false} onSubmit={submit} onCancel={vi.fn()} />,
    )
    await event.selectOptions(screen.getByLabelText('Lý do'), 'OTHER')
    await event.type(screen.getByLabelText('Ghi chú (không bắt buộc)'), ' ghi chú ')
    await event.click(screen.getByRole('button', { name: 'Gửi báo lỗi' }))
    expect(submit).toHaveBeenCalledWith({ reason: 'OTHER', note: ' ghi chú ' })
  })
  it('counts code points, permits 300 and blocks 301', async () => {
    const event = userEvent.setup()
    const submit = vi.fn()
    render(
      <ReferenceReportForm pending={false} sent={false} onSubmit={submit} onCancel={vi.fn()} />,
    )
    const note = screen.getByLabelText('Ghi chú (không bắt buộc)')
    await event.click(note)
    await event.paste('😀'.repeat(300))
    expect(screen.getByText('300/300 ký tự')).toBeVisible()
    await event.click(screen.getByRole('button', { name: 'Gửi báo lỗi' }))
    expect(submit).toHaveBeenCalledOnce()
    await event.click(note)
    await event.paste('😀')
    expect(screen.getByRole('button', { name: 'Gửi báo lỗi' })).toBeDisabled()
    expect(screen.getByText('Ghi chú tối đa 300 ký tự.')).toBeVisible()
  })
  it('disables controls while pending and shows success without another send', () => {
    const props = { onSubmit: vi.fn(), onCancel: vi.fn() }
    const { rerender } = render(<ReferenceReportForm {...props} pending sent={false} />)
    expect(screen.getByRole('button', { name: 'Gửi báo lỗi' })).toBeDisabled()
    expect(screen.getByLabelText('Lý do')).toBeDisabled()
    rerender(<ReferenceReportForm {...props} pending={false} sent />)
    expect(screen.getByRole('status')).toHaveTextContent('Đã ghi nhận báo lỗi.')
    expect(screen.queryByRole('button', { name: 'Gửi báo lỗi' })).not.toBeInTheDocument()
  })
  it.each([
    ['RATE_LIMITED', 429, 'Bạn đã báo lỗi tài liệu này hôm nay. Vui lòng quay lại vào ngày mai.'],
    ['FORBIDDEN', 403, 'Không có quyền báo lỗi.'],
    ['REFERENCE_NOT_FOUND', 404, 'Tài liệu không còn công khai.'],
    ['VALIDATION_FAILED', 422, 'Nội dung báo lỗi không hợp lệ.'],
  ])('shows %s without pretending success', (errorCode, status, message) => {
    render(
      <ReferenceReportForm
        pending={false}
        sent={false}
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
        error={
          new ApiError({
            errorCode: String(errorCode),
            status: Number(status),
            message: String(message),
          })
        }
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent(String(message))
    expect(screen.queryByText('Đã ghi nhận báo lỗi.')).not.toBeInTheDocument()
  })
})
