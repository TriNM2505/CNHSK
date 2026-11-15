import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { formFromExam } from '../examForm'
import { examFixture } from '../test-fixtures'
import { ExamSectionsEditor } from './ExamSectionsEditor'
describe('section controls', () => {
  function form() {
    const value = formFromExam(examFixture)
    value.sections.push({ key: 'second', name: 'Viết', type: 'WRITING', durationMin: '0' })
    return value
  }
  it('reorders by keyboard buttons and native drag, preserving question references', () => {
    const value = form()
    const onChange = vi.fn()
    render(
      <MemoryRouter>
        <ExamSectionsEditor form={value} disabled={false} onChange={onChange} />
      </MemoryRouter>,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Đưa phần 2 lên' }))
    expect(onChange.mock.lastCall?.[0].sections[0].key).toBe('second')
    const transfer = { getData: () => '0' }
    fireEvent.drop(screen.getAllByRole('group')[1]!, { dataTransfer: transfer })
    expect(onChange.mock.lastCall?.[0].questions[0].sectionKey).toBe(value.sections[0]!.key)
    expect(onChange.mock.lastCall?.[0].sections[1].key).toBe(value.sections[0]!.key)
  })
  it('requires confirmation before removing a section with links', () => {
    const onChange = vi.fn()
    render(
      <MemoryRouter>
        <ExamSectionsEditor form={form()} disabled={false} onChange={onChange} />
      </MemoryRouter>,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Bỏ phần 1' }))
    expect(onChange).not.toHaveBeenCalled()
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Bỏ phần và liên kết' }),
    )
    expect(onChange.mock.lastCall?.[0]).toMatchObject({
      questions: [],
      sections: [{ key: 'second' }],
    })
  })
  it('enforces the maximum and readonly controls', () => {
    const value = form()
    value.sections = Array.from({ length: 5 }, (_, i) => ({
      ...value.sections[0]!,
      key: String(i),
    }))
    render(
      <MemoryRouter>
        <ExamSectionsEditor form={value} disabled onChange={vi.fn()} />
      </MemoryRouter>,
    )
    expect(screen.getByRole('button', { name: 'Thêm phần' })).toBeDisabled()
    expect(screen.getByLabelText('Tên phần 1', { exact: false })).toBeDisabled()
  })
})
