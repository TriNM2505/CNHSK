import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { Button } from '@/shared/ui/Button'
import { ConfirmPanel } from '@/shared/ui/ConfirmPanel'

function ConfirmPanelHarness({ onConfirm = vi.fn() }: { onConfirm?: () => void }) {
  const [open, setOpen] = useState(false)

  return (
    <>
      <Button onClick={() => setOpen(true)}>Mở xác nhận</Button>
      <ConfirmPanel
        confirmLabel="Xoá"
        danger
        message="Dữ liệu đã xoá không thể khôi phục."
        onCancel={() => setOpen(false)}
        onConfirm={onConfirm}
        open={open}
        title="Xoá mục này?"
      />
    </>
  )
}

describe('ConfirmPanel', () => {
  it('has dialog semantics, locks background scrolling and traps focus', async () => {
    const user = userEvent.setup()
    render(<ConfirmPanelHarness />)

    const trigger = screen.getByRole('button', { name: 'Mở xác nhận' })
    await user.click(trigger)

    const dialog = screen.getByRole('dialog', { name: 'Xoá mục này?' })
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(document.body).toHaveStyle({ overflow: 'hidden' })
    expect(screen.getByRole('button', { name: 'Huỷ' })).toHaveFocus()

    await user.tab()
    expect(screen.getByRole('button', { name: 'Xoá' })).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('button', { name: 'Huỷ' })).toHaveFocus()
  })

  it('cancels with Escape, restores scrolling and returns focus to the trigger', async () => {
    const user = userEvent.setup()
    render(<ConfirmPanelHarness />)

    const trigger = screen.getByRole('button', { name: 'Mở xác nhận' })
    await user.click(trigger)
    await user.keyboard('{Escape}')

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(document.body.style.overflow).toBe('')
    expect(trigger).toHaveFocus()
  })

  it('locks both actions and ignores Escape and confirmation while pending', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()
    const onConfirm = vi.fn()
    render(
      <ConfirmPanel
        confirmLabel="Cấm tài khoản"
        message="Yêu cầu đang được xử lý."
        onCancel={onCancel}
        onConfirm={onConfirm}
        open
        pending
        title="Xác nhận cấm"
      />,
    )

    const cancelButton = screen.getByRole('button', { name: 'Huỷ' })
    const confirmButton = screen.getByRole('button', { name: 'Đang xử lý' })
    expect(cancelButton).toBeDisabled()
    expect(confirmButton).toBeDisabled()

    await user.keyboard('{Escape}')
    await user.click(confirmButton)

    expect(onCancel).not.toHaveBeenCalled()
    expect(onConfirm).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('traps focus between a textarea in the message and both action buttons', async () => {
    const user = userEvent.setup()
    render(
      <ConfirmPanel
        confirmLabel="Cấm tài khoản"
        message={<textarea aria-label="Lý do cấm" />}
        onCancel={vi.fn()}
        onConfirm={vi.fn()}
        open
        title="Xác nhận cấm"
      />,
    )

    const textarea = screen.getByRole('textbox', { name: 'Lý do cấm' })
    const cancelButton = screen.getByRole('button', { name: 'Huỷ' })
    const confirmButton = screen.getByRole('button', { name: 'Cấm tài khoản' })
    expect(cancelButton).toHaveFocus()

    await user.tab({ shift: true })
    expect(textarea).toHaveFocus()
    await user.tab({ shift: true })
    expect(confirmButton).toHaveFocus()
    await user.tab()
    expect(textarea).toHaveFocus()
  })
})
