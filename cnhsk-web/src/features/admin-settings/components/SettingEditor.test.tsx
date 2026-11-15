import { useState } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { settingFixture, settingsFixtures } from '../test-fixtures'
import type { SettingDraft } from '../types'
import { SettingEditor } from './SettingEditor'

function open(mode: SettingDraft['mode'] = 'edit', pending = false) {
  const submit = vi.fn()
  const cancel = vi.fn()
  function Fixture() {
    const [draft, setDraft] = useState<SettingDraft>({
      key: settingFixture.key,
      mode,
      value: '20',
      reason: '',
      confirming: false,
    })
    return (
      <SettingEditor
        item={settingFixture}
        catalog={settingsFixtures}
        draft={draft}
        pending={pending}
        onChange={setDraft}
        onSubmit={submit}
        onCancel={cancel}
      />
    )
  }
  render(<Fixture />)
  return { submit, cancel }
}
describe('inline setting confirmation', () => {
  it('requires reason and valid integer before confirmation, then displays before/after', () => {
    const { submit } = open()
    expect(screen.getByRole('button', { name: 'Tiếp tục' })).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Lý do', { exact: false }), {
      target: { value: 'Tăng hạn mức.' },
    })
    fireEvent.change(screen.getByLabelText('Giá trị mới', { exact: false }), {
      target: { value: '-1' },
    })
    expect(screen.getByRole('button', { name: 'Tiếp tục' })).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Giá trị mới', { exact: false }), {
      target: { value: '30' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }))
    expect(submit).not.toHaveBeenCalled()
    expect(screen.getByText('20 → 30')).toBeVisible()
    expect(screen.getByLabelText('Lý do', { exact: false })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận lưu' }))
    expect(submit).toHaveBeenCalledTimes(1)
  })
  it('allows correcting confirmation without losing the draft or writing', () => {
    const { submit } = open()
    fireEvent.change(screen.getByLabelText('Lý do', { exact: false }), {
      target: { value: 'Giữ lý do.' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }))
    fireEvent.click(screen.getByRole('button', { name: 'Sửa lại' }))
    expect(screen.getByLabelText('Lý do', { exact: false })).toHaveValue('Giữ lý do.')
    expect(screen.getByLabelText('Lý do', { exact: false })).toBeEnabled()
    expect(submit).not.toHaveBeenCalled()
  })
  it('requires reason even when resetting an already-default setting', () => {
    const { submit } = open('reset')
    expect(screen.queryByLabelText('Giá trị mới', { exact: false })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tiếp tục' })).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Lý do', { exact: false }), {
      target: { value: 'Về chuẩn.' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }))
    expect(screen.getByText('20 → 20 (mặc định)')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận lưu' }))
    expect(submit).toHaveBeenCalledOnce()
  })
  it('disables controls and cancel during a pending write', () => {
    const { cancel } = open('edit', true)
    expect(screen.getByLabelText('Giá trị mới', { exact: false })).toBeDisabled()
    expect(screen.getByLabelText('Lý do', { exact: false })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Hủy' }))
    expect(cancel).not.toHaveBeenCalled()
  })
})
