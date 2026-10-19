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
})
