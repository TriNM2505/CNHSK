import { describe, expect, it } from 'vitest'
import { ApiError } from '@/shared/api'
import {
  emptyForm,
  examWrite,
  failureMessage,
  formFromExam,
  move,
  moveQuestion,
  pointsTotal,
  publicationErrors,
  removeSection,
  validateForm,
} from './examForm'
import { examFixture } from './test-fixtures'
describe('exam draft model', () => {
  it('keeps declared score independent from the live question sum and permits empty draft questions', () => {
    const form = formFromExam(examFixture)
    form.totalScore = '100'
    expect(pointsTotal(form)).toBe(10)
    expect(examWrite(form).totalScore).toBe(100)
    form.questions = []
    expect(examWrite(form).questions).toEqual([])
  })
  it('does not guess missing legacy fields, including zero versus null duration', () => {
    const form = formFromExam({
      ...examFixture,
      sections: [{ name: 'Cũ', type: null, durationMin: null }],
    })
    expect(form.sections[0]).toMatchObject({ type: '', durationMin: '' })
    expect(validateForm(form).join(' ')).toContain('bổ sung')
    form.sections[0]!.type = 'READING'
    form.sections[0]!.durationMin = '0'
    expect(examWrite(form).sections[0]?.durationMin).toBe(0)
  })
  it('remaps indices by stable section key, keeps points and omits read metadata', () => {
    const form = formFromExam(examFixture)
    form.sections.push({ key: 'second', name: 'Viết', type: 'WRITING', durationMin: '10' })
    form.sections = move(form.sections, 0, 1)
    expect(examWrite(form).questions).toEqual([{ questionId: 901, sectionIndex: 1, points: 10 }])
    const moved = moveQuestion(form, 901, 'second')
    expect(examWrite(moved).questions[0]?.sectionIndex).toBe(0)
    expect(examWrite(removeSection(moved, 'second')).questions).toEqual([])
  })
  it.each(['', '0', '1.5', '101', 'NaN'])('rejects invalid per-question points %s', (points) => {
    const form = formFromExam(examFixture)
    form.questions[0]!.points = points
    expect(validateForm(form).length).toBeGreaterThan(0)
  })
  it('checks all write bounds and global duplicate question IDs', () => {
    expect(validateForm(emptyForm()).length).toBeGreaterThan(0)
    const form = formFromExam(examFixture)
    form.questions.push({ ...form.questions[0]! })
    expect(validateForm(form).join(' ')).toContain('không trùng')
    form.questions = Array.from({ length: 301 }, (_, i) => ({
      ...form.questions[0]!,
      questionId: i + 1,
    }))
    form.sections = Array.from({ length: 6 }, (_, i) => ({ ...form.sections[0]!, key: String(i) }))
    form.totalScore = '1001'
    form.hskLevel = '7'
    form.code = 'không hợp lệ'
    form.title = ' '
    expect(validateForm(form).join(' ')).toMatch(/300/)
    expect(() => examWrite(form)).toThrow()
    expect(move(form.sections, -1, 1)).toBe(form.sections)
  })
  it('keeps server errors and exact conflict wording', () => {
    const errors = [{ code: 'EMPTY_SECTION', message: 'Trống', sectionIndex: 0 }, { bad: true }]
    expect(publicationErrors(errors)).toHaveLength(1)
    expect(publicationErrors({ path: [] })).toEqual([])
    expect(
      failureMessage(new ApiError({ status: 409, errorCode: 'EDIT_CONFLICT', message: 'x' })),
    ).toBe('Đề vừa được người khác sửa, tải lại')
  })
})
