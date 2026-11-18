import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ExamAttemptView } from './ExamAttemptView'

function renderAttempt(readOnly = false) {
  const onSubmit = vi.fn()
  render(
    <MemoryRouter initialEntries={['/learn/exams/sessions/1']}>
      <Routes>
        <Route
          path="/learn/exams/sessions/1"
          element={
            <ExamAttemptView
              title="Đề mẫu"
              sections={[
                {
                  index: 1,
                  name: 'Đọc',
                  questions: [{ no: 1, text: '你好', options: [{ key: 'A', text: 'Xin chào' }] }],
                },
              ]}
              currentNo={1}
              choices={{}}
              marks={[]}
              placement={false}
              pending={false}
              readOnly={readOnly}
              confirming={false}
              onNavigate={vi.fn()}
              onChoose={vi.fn()}
              onSkip={vi.fn()}
              onMark={vi.fn()}
              onConfirmChange={vi.fn()}
              onSubmit={onSubmit}
              onContinue={vi.fn()}
            />
          }
        />
        <Route path="/learn/exams" element={<p>Danh sách đề</p>} />
      </Routes>
    </MemoryRouter>,
  )
  return { onSubmit }
}

describe('exam session exit', () => {
  it('keeps an unsubmitted session until exit is confirmed and never submits it on exit', async () => {
    const user = userEvent.setup()
    const { onSubmit } = renderAttempt()
    await user.click(screen.getByRole('link', { name: 'Thoát' }))
    expect(screen.getByRole('dialog', { name: 'Rời bài làm?' })).toBeVisible()
    expect(screen.queryByText('Danh sách đề')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Tiếp tục làm bài' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Đề mẫu' })).toBeVisible()
    await user.click(screen.getByRole('link', { name: 'Thoát' }))
    await user.click(screen.getByRole('button', { name: 'Rời bài làm' }))
    expect(screen.getByText('Danh sách đề')).toBeVisible()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('allows a submitted read-only session to exit directly', async () => {
    const user = userEvent.setup()
    const { onSubmit } = renderAttempt(true)
    expect(screen.getByRole('button', { name: 'A. Xin chào' })).toBeDisabled()
    await user.click(screen.getByRole('link', { name: 'Thoát' }))
    expect(screen.getByText('Danh sách đề')).toBeVisible()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })
})
