import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { FormField, Input, PasswordInput, Select, Textarea } from './FormField'

describe('FormField and controls', () => {
  it('connects label, hint and error to the input', () => {
    render(
      <FormField
        error="Email không hợp lệ"
        hint="Dùng email đang hoạt động"
        htmlFor="email"
        label="Email"
        required
      >
        <Input type="email" />
      </FormField>,
    )

    const input = screen.getByRole('textbox', { name: 'Email' })
    expect(input).toHaveAttribute('aria-describedby', 'email-hint email-error')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAttribute('aria-required', 'true')
    expect(screen.getByText('Email không hợp lệ')).toBeVisible()
  })

  it('renders select and textarea controls with labels', () => {
    render(
      <>
        <FormField htmlFor="level" label="Cấp HSK">
          <Select>
            <option>HSK 1</option>
          </Select>
        </FormField>
        <FormField htmlFor="note" label="Ghi chú">
          <Textarea />
        </FormField>
      </>,
    )

    expect(screen.getByRole('combobox', { name: 'Cấp HSK' })).toBeVisible()
    expect(screen.getByRole('textbox', { name: 'Ghi chú' })).toBeVisible()
  })

  it('shows and hides a password with an accessible toggle', async () => {
    const user = userEvent.setup()
    render(
      <FormField htmlFor="password" label="Mật khẩu">
        <PasswordInput />
      </FormField>,
    )

    const input = screen.getByLabelText('Mật khẩu')
    expect(input).toHaveAttribute('type', 'password')
    await user.click(screen.getByRole('button', { name: 'Hiện mật khẩu' }))
    expect(input).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Ẩn mật khẩu' })).toBeVisible()
  })
})
