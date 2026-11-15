import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { historyFixture } from '../test-fixtures'
import { SettingHistory, type SettingHistoryProps } from './SettingHistory'

const base: SettingHistoryProps = {
  settingKey: 'assistant.daily-limit',
  state: 'ready',
  data: historyFixture,
  onPage: vi.fn(),
  onRetry: vi.fn(),
  onClose: vi.fn(),
}
describe('setting audit history', () => {
  it('preserves server order, displays reset null and falls back to actor ID', () => {
    render(<SettingHistory {...base} />)
    const rows = screen.getAllByRole('listitem')
    expect(rows[0]).toHaveTextContent('25 → Về mặc định')
    expect(rows[0]).toHaveTextContent('Người dùng #31')
    expect(rows[1]).toHaveTextContent('20 → 25')
  })
  it('converts pagination back to zero-based server page', () => {
    const onPage = vi.fn()
    render(
      <SettingHistory {...base} data={{ ...historyFixture, totalElements: 21 }} onPage={onPage} />,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Sau' }))
    expect(onPage).toHaveBeenCalledWith(1)
  })
  it('displays empty history without inventing rows', () => {
    render(<SettingHistory {...base} data={{ ...historyFixture, items: [] }} />)
    expect(screen.getByText('Chưa có lịch sử')).toBeVisible()
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
  })
  it('offers a separate history retry and close', () => {
    const onRetry = vi.fn(),
      onClose = vi.fn()
    render(
      <SettingHistory
        {...base}
        state="error"
        error={{ message: 'Lỗi lịch sử', requestId: 'audit-1' }}
        onRetry={onRetry}
        onClose={onClose}
      />,
    )
    expect(screen.getByText('Lỗi lịch sử')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: /Thử lại/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Đóng lịch sử' }))
    expect(onRetry).toHaveBeenCalledOnce()
    expect(onClose).toHaveBeenCalledOnce()
  })
})
