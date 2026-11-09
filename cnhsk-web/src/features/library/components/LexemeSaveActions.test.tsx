import { fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import type { LexemeDetail } from '../types'
import { LexemeSaveActions } from './LexemeSaveActions'

const entry: LexemeDetail = {
  id: 101,
  kind: 'WORD',
  text: '自作',
  pinyin: 'zì zuò',
  meaningVi: 'Tự soạn',
  examples: [],
  components: [],
  containedIn: [],
  related: [],
}

function setup(noteId: unknown = 42) {
  const create = vi.fn().mockRejectedValue(
    new ApiError({
      status: 409,
      errorCode: 'NOTE_DUPLICATE',
      message: 'Trùng từ',
      details: { noteId },
    }),
  )
  const router = createMemoryRouter(
    [
      {
        path: '/',
        element: (
          <LexemeSaveActions
            entry={entry}
            access="learner"
            loginTo="/login"
            onReloadAuth={vi.fn()}
            onCreateNote={create}
            onLoadDecks={async () => []}
            onAddCard={vi.fn()}
          />
        ),
      },
      { path: '/library/notes', element: <h1>Sổ tay đích</h1> },
    ],
    { initialEntries: ['/'] },
  )
  render(<RouterProvider router={router} />)
  return { router, create }
}

describe('saving an existing notebook lexeme', () => {
  it('keeps the draft and only navigates after the learner clicks the existing-note link', async () => {
    const { router, create } = setup()
    fireEvent.click(screen.getByRole('button', { name: 'Lưu vào sổ tay' }))
    fireEvent.change(screen.getByLabelText(/^Nội dung ghi chú/), {
      target: { value: 'Bản nháp tự soạn cần giữ' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu ghi chú' }))
    await screen.findByText('Từ này đã có trong sổ tay')
    expect(screen.getByLabelText(/^Nội dung ghi chú/)).toHaveValue('Bản nháp tự soạn cần giữ')
    expect(router.state.location.pathname).toBe('/')
    expect(create).toHaveBeenCalledOnce()
    fireEvent.click(screen.getByRole('link', { name: 'Mở ghi chú đã có' }))
    await screen.findByRole('heading', { name: 'Sổ tay đích' })
    expect(router.state.location.search).toBe('?noteId=42')
  })

  it.each([0, -1, 1.5, '42', null, Number.MAX_SAFE_INTEGER + 1])(
    'never offers navigation for invalid details.noteId=%s',
    async (id) => {
      setup(id)
      fireEvent.click(screen.getByRole('button', { name: 'Lưu vào sổ tay' }))
      fireEvent.click(screen.getByRole('button', { name: 'Lưu ghi chú' }))
      await screen.findByText('Từ này đã có trong sổ tay')
      expect(screen.queryByRole('link', { name: 'Mở ghi chú đã có' })).not.toBeInTheDocument()
      expect(screen.getByLabelText(/^Nội dung ghi chú/)).toHaveValue('自作\nzì zuò\nTự soạn')
    },
  )
})
