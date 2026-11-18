import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { ContestEditorView } from './ContestEditorView'

describe('ContestEditorView', () => {
  it('keeps the return link with the current list filter', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/content/contests/new']}>
        <Routes>
          <Route
            path="/content/contests/new"
            element={
              <ContestEditorView listPath="/content/contests?status=DRAFT" title="Tạo cuộc thi">
                <p role="status">Đang giữ bản nháp</p>
              </ContestEditorView>
            }
          />
          <Route path="/content/contests" element={<h1>Danh sách cuộc thi</h1>} />
        </Routes>
      </MemoryRouter>,
    )
    expect(screen.getByRole('heading', { name: 'Tạo cuộc thi', level: 1 })).toBeVisible()
    expect(screen.getByRole('status')).toHaveTextContent('Đang giữ bản nháp')
    const link = screen.getByRole('link', { name: 'Về danh sách cuộc thi' })
    expect(link).toHaveAttribute('href', '/content/contests?status=DRAFT')
    await user.click(link)
    expect(screen.getByRole('heading', { name: 'Danh sách cuộc thi' })).toBeVisible()
  })

  it('preserves the edit title, subtitle and alert supplied by the page', () => {
    render(
      <MemoryRouter>
        <ContestEditorView
          listPath="/content/contests"
          title="Chi tiết cuộc thi"
          subtitle="Thi HSK 2"
        >
          <p role="alert">Cuộc thi đã bị khoá sửa theo phản hồi máy chủ.</p>
        </ContestEditorView>
      </MemoryRouter>,
    )
    expect(screen.getByRole('heading', { name: 'Chi tiết cuộc thi', level: 1 })).toBeVisible()
    expect(screen.getByText('Thi HSK 2')).toBeVisible()
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Cuộc thi đã bị khoá sửa theo phản hồi máy chủ.',
    )
  })
})
