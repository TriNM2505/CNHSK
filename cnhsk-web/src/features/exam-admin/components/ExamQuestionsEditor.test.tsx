import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { formFromExam } from '../examForm'
import { examFixture } from '../test-fixtures'
import { ExamQuestionsEditor } from './ExamQuestionsEditor'
describe('question ordering and points', () => {
  it('moves a question across sections without changing its ID or points', () => {
    const form = formFromExam(examFixture)
    form.sections.push({ key: 'second', name: 'Viết', type: 'WRITING', durationMin: '0' })
    const onChange = vi.fn()
    render(<ExamQuestionsEditor form={form} disabled={false} onChange={onChange} />)
    fireEvent.change(screen.getByLabelText('Phần của câu CAU-901'), { target: { value: 'second' } })
    expect(onChange.mock.lastCall?.[0].questions[0]).toMatchObject({
      questionId: 901,
      sectionKey: 'second',
      points: '10',
    })
    fireEvent.change(screen.getByLabelText('Điểm câu CAU-901'), { target: { value: '20' } })
    expect(onChange.mock.lastCall?.[0].questions[0].points).toBe('20')
  })
  it('supports drag order and unlinking while readonly disables writes', () => {
    const form = formFromExam(examFixture)
    form.questions.push({ ...form.questions[0]!, questionId: 902, code: 'Q2' })
    const onChange = vi.fn()
    const view = render(<ExamQuestionsEditor form={form} disabled={false} onChange={onChange} />)
    fireEvent.drop(screen.getAllByRole('article')[0]!, { dataTransfer: { getData: () => '902' } })
    expect(
      onChange.mock.lastCall?.[0].questions.map((q: { questionId: number }) => q.questionId),
    ).toEqual([902, 901])
    fireEvent.click(screen.getByRole('button', { name: 'Bỏ câu Q2' }))
    expect(onChange.mock.lastCall?.[0].questions).toHaveLength(1)
    view.rerender(<ExamQuestionsEditor form={form} disabled onChange={onChange} />)
    expect(screen.getByLabelText('Điểm câu CAU-901')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Bỏ câu Q2' })).toBeDisabled()
  })
})
