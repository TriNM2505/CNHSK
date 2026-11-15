import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getQuestion, listQuestions } from '@/features/question-bank/api/questions'
import { formFromExam } from '../examForm'
import { examFixture, questionFixture, questionPageFixture } from '../test-fixtures'
import { ExamQuestionPicker } from './ExamQuestionPicker'
vi.mock('@/features/question-bank/api/questions', () => ({
  listQuestions: vi.fn(),
  getQuestion: vi.fn(),
}))
beforeEach(() => {
  vi.mocked(listQuestions).mockReset().mockResolvedValue(questionPageFixture)
  vi.mocked(getQuestion).mockReset().mockResolvedValue(questionFixture)
})
function picker(selectedIds: number[] = []) {
  const onAdd = vi.fn()
  render(
    <ExamQuestionPicker
      sections={formFromExam(examFixture).sections}
      selectedIds={selectedIds}
      disabled={false}
      onAdd={onAdd}
      onClose={vi.fn()}
    />,
  )
  return onAdd
}
describe('approved question selector', () => {
  it('fixes APPROVED, applies KID/type filters and paginates without losing selected IDs', async () => {
    vi.mocked(listQuestions).mockResolvedValue({ ...questionPageFixture, totalElements: 21 })
    picker([902])
    await screen.findByRole('button', { name: 'Đã chọn' })
    expect(screen.getByRole('button', { name: 'Đã chọn' })).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Lọc KID'), { target: { value: 'VB1-001' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tìm câu hỏi' }))
    await waitFor(() =>
      expect(listQuestions).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: 'APPROVED', kid: 'VB1-001', page: 0 }),
        expect.any(AbortSignal),
      ),
    )
    await screen.findByRole('button', { name: 'Đã chọn' })
    fireEvent.click(screen.getByRole('button', { name: 'Sau' }))
    await waitFor(() =>
      expect(listQuestions).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: 'APPROVED', page: 1 }),
        expect.any(AbortSignal),
      ),
    )
    expect(getQuestion).not.toHaveBeenCalled()
  })
  it('checks fresh status/KID before adding and guards repeated requests', async () => {
    const onAdd = picker()
    const button = await screen.findByRole('button', { name: 'Thêm CAU-902' })
    fireEvent.click(button)
    fireEvent.click(button)
    await waitFor(() => expect(onAdd).toHaveBeenCalledTimes(1))
    expect(getQuestion).toHaveBeenCalledTimes(1)
    expect(onAdd).toHaveBeenCalledWith(
      expect.objectContaining({ questionId: 902, points: '1', kid: 'VB1-001', status: 'APPROVED' }),
    )
  })
  it('rejects a question archived after the list response', async () => {
    vi.mocked(getQuestion).mockResolvedValue({ ...questionFixture, status: 'ARCHIVED' })
    const add = picker()
    fireEvent.click(await screen.findByRole('button', { name: 'Thêm CAU-902' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('vừa đổi trạng thái')
    expect(add).not.toHaveBeenCalled()
  })
  it('enforces 300 across pages', async () => {
    picker(Array.from({ length: 300 }, (_, i) => i + 1))
    expect(await screen.findByRole('button', { name: 'Thêm CAU-902' })).toBeDisabled()
    expect(getQuestion).not.toHaveBeenCalled()
  })
})
