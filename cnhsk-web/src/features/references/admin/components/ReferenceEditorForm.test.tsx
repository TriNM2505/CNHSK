import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import { draftOf } from '../referenceForm'
import { draftReference, publishedReference } from '../test-fixtures'
import { ReferenceEditorForm } from './ReferenceEditorForm'

describe('ReferenceEditorForm', () => {
  it('retains book draft across kind changes but emits null/false for a podcast', async () => {
    const event = userEvent.setup()
    const submit = vi.fn()
    render(
      <ReferenceEditorForm
        initial={draftOf(publishedReference)}
        editing
        pending={false}
        onSubmit={submit}
        onCancel={vi.fn()}
      />,
    )
    expect(screen.getByLabelText('Tác giả')).toHaveValue(publishedReference.author)
    await event.selectOptions(screen.getByLabelText('Loại'), 'PODCAST')
    expect(screen.queryByLabelText('Tác giả')).not.toBeInTheDocument()
    await event.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(submit).toHaveBeenLastCalledWith(
      expect.objectContaining({
        author: null,
        publisher: null,
        publishedYear: null,
        isAffiliate: false,
        kind: 'PODCAST',
      }),
    )
    await event.selectOptions(screen.getByLabelText('Loại'), 'BOOK')
    expect(screen.getByLabelText('Tác giả')).toHaveValue(publishedReference.author)
    expect(screen.getByLabelText('Link tiếp thị liên kết')).toBeChecked()
  })
  it('offers new levels 1–3 while preserving existing 4–9 values', async () => {
    const event = userEvent.setup()
    const submit = vi.fn()
    render(
      <ReferenceEditorForm
        initial={draftOf({ ...publishedReference, hskMin: 4, hskMax: 9 })}
        editing
        pending={false}
        onSubmit={submit}
        onCancel={vi.fn()}
      />,
    )
    expect(screen.getByLabelText('HSK tối thiểu')).toHaveValue('4')
    expect(
      within(screen.getByLabelText('HSK tối thiểu')).getByRole('option', {
        name: 'HSK 4 (giá trị hiện có)',
      }),
    ).toBeDisabled()
    await event.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(submit).toHaveBeenCalledWith(expect.objectContaining({ hskMin: 4, hskMax: 9 }))
    await event.selectOptions(screen.getByLabelText('HSK tối thiểu'), '2')
    await event.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(submit).toHaveBeenLastCalledWith(expect.objectContaining({ hskMin: 2, hskMax: 9 }))
  })
  it.each([
    'MALFORMED_URL',
    'NON_HTTPS_LINK',
    'DOMAIN_NOT_ALLOWED',
    'PUBLISH_INVALID_RESOURCE',
    'VALIDATION_FAILED',
  ])('shows %s by field and retains input', (errorCode) => {
    render(
      <ReferenceEditorForm
        initial={draftOf(draftReference)}
        editing
        pending={false}
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
        error={
          new ApiError({
            status: 422,
            errorCode,
            message: 'Không lưu được.',
            details: [{ field: 'url', message: 'Sửa liên kết này.' }],
          })
        }
      />,
    )
    expect(screen.getByLabelText('Liên kết')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Liên kết')).toHaveValue(draftReference.url)
    expect(screen.getByText('Sửa liên kết này.')).toBeVisible()
    expect(screen.getByRole('alert')).toHaveTextContent('Không lưu được.')
  })
  it('adds/removes exact tags and does not silently discard an unadded tag', async () => {
    const event = userEvent.setup()
    const submit = vi.fn()
    render(
      <ReferenceEditorForm
        initial={draftOf({ ...draftReference, tags: [] })}
        editing={false}
        pending={false}
        onSubmit={submit}
        onCancel={vi.fn()}
      />,
    )
    await event.type(screen.getByLabelText('Tag mới'), ' nghe ')
    await event.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(submit).not.toHaveBeenCalled()
    await event.click(screen.getByRole('button', { name: 'Thêm tag' }))
    await event.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(submit).toHaveBeenCalledWith(expect.objectContaining({ tags: [' nghe '] }))
    await event.click(screen.getByRole('button', { name: /Bỏ tag/ }))
    await event.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(submit).toHaveBeenLastCalledWith(expect.objectContaining({ tags: [] }))
  })
  it('disables saving, cancellation and form controls while pending', () => {
    render(
      <ReferenceEditorForm
        initial={draftOf(draftReference)}
        editing
        pending
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
      />,
    )
    expect(screen.getByRole('button', { name: 'Lưu' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Huỷ sửa' })).toBeDisabled()
    expect(screen.getByLabelText('Tên tài nguyên')).toBeDisabled()
  })
})
