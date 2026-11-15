import { toApiError } from '@/shared/api'
import type { DraftSection, ExamDetail, ExamForm, ExamWrite, PublicationError } from './types'

export const statusLabels = { DRAFT: 'Nháp', PUBLISHED: 'Đã công bố', ARCHIVED: 'Đã lưu trữ' }
export const sectionLabels = { LISTENING: 'Nghe', READING: 'Đọc', WRITING: 'Viết' }
let sequence = 0
export function newSection() {
  return { key: `section-${sequence++}`, name: '', type: '' as const, durationMin: '' }
}
export function emptyForm(): ExamForm {
  return {
    code: '',
    title: '',
    hskLevel: '',
    totalScore: '',
    sections: [newSection()],
    questions: [],
  }
}
export function formFromExam(exam: ExamDetail): ExamForm {
  const sections: DraftSection[] = exam.sections.map((section) => ({
    ...section,
    key: newSection().key,
    type: section.type ?? '',
    durationMin: section.durationMin === null ? '' : String(section.durationMin),
  }))
  return {
    code: exam.code,
    title: exam.title,
    hskLevel: String(exam.hskLevel),
    totalScore: String(exam.totalScore),
    sections,
    questions: [...exam.questions]
      .sort((a, b) => a.orderIndex - b.orderIndex)
      .map((q) => ({
        ...q,
        sectionKey: sections[q.sectionIndex]?.key ?? '',
        points: String(q.points),
      })),
  }
}
export function pointsTotal(form: ExamForm) {
  return form.questions.reduce(
    (total, q) => total + (/^\d+$/.test(q.points) ? Number(q.points) : 0),
    0,
  )
}
function integer(value: string, min: number, max: number) {
  return (
    /^\d+$/.test(value) &&
    Number.isSafeInteger(Number(value)) &&
    Number(value) >= min &&
    Number(value) <= max
  )
}
export function validateForm(form: ExamForm): string[] {
  const errors: string[] = []
  if (!/^[A-Z0-9-]{1,50}$/.test(form.code))
    errors.push('Mã đề gồm 1–50 chữ A–Z, chữ số hoặc dấu gạch ngang.')
  if (!form.title.trim() || Array.from(form.title.trim()).length > 200)
    errors.push('Tiêu đề cần từ 1 đến 200 ký tự.')
  if (!integer(form.hskLevel, 1, 6)) errors.push('Chọn cấp HSK hợp lệ.')
  if (!integer(form.totalScore, 1, 1000))
    errors.push('Tổng điểm khai báo phải là số nguyên từ 1 đến 1.000.')
  if (form.sections.length < 1 || form.sections.length > 5) errors.push('Đề cần từ 1 đến 5 phần.')
  form.sections.forEach((section, index) => {
    if (!section.name.trim() || Array.from(section.name.trim()).length > 100)
      errors.push(`Phần ${index + 1}: nhập tên từ 1 đến 100 ký tự.`)
    if (!section.type) errors.push(`Phần ${index + 1}: bổ sung loại phần thi.`)
    if (!integer(section.durationMin, 0, 180))
      errors.push(`Phần ${index + 1}: bổ sung thời lượng nguyên từ 0 đến 180 phút.`)
  })
  if (form.questions.length > 300) errors.push('Mỗi đề có tối đa 300 câu hỏi.')
  const ids = new Set<number>()
  form.questions.forEach((q) => {
    if (!Number.isSafeInteger(q.questionId) || q.questionId <= 0 || ids.has(q.questionId))
      errors.push('Câu hỏi phải có mã hợp lệ và không trùng trong đề.')
    ids.add(q.questionId)
    if (!form.sections.some((s) => s.key === q.sectionKey))
      errors.push('Câu hỏi phải thuộc một phần của đề.')
    if (!integer(q.points, 1, 100))
      errors.push(`Câu ${q.code ?? q.questionId}: điểm phải là số nguyên từ 1 đến 100.`)
  })
  return [...new Set(errors)]
}
export function examWrite(form: ExamForm): ExamWrite {
  const errors = validateForm(form)
  if (errors.length) throw new Error(errors.join(' '))
  return {
    code: form.code,
    title: form.title.trim(),
    hskLevel: Number(form.hskLevel),
    totalScore: Number(form.totalScore),
    sections: form.sections.map((s) => ({
      name: s.name.trim(),
      type: s.type as 'LISTENING' | 'READING' | 'WRITING',
      durationMin: Number(s.durationMin),
    })),
    questions: form.sections.flatMap((s, sectionIndex) =>
      form.questions
        .filter((q) => q.sectionKey === s.key)
        .map((q) => ({ questionId: q.questionId, sectionIndex, points: Number(q.points) })),
    ),
  }
}
export function move<T>(items: T[], from: number, to: number): T[] {
  if (from < 0 || to < 0 || from >= items.length || to >= items.length) return items
  const result = [...items]
  const [item] = result.splice(from, 1)
  result.splice(to, 0, item!)
  return result
}
export function removeSection(form: ExamForm, key: string): ExamForm {
  return {
    ...form,
    sections: form.sections.filter((s) => s.key !== key),
    questions: form.questions.filter((q) => q.sectionKey !== key),
  }
}
export function moveQuestion(
  form: ExamForm,
  id: number,
  sectionKey: string,
  before?: number,
): ExamForm {
  const item = form.questions.find((q) => q.questionId === id)
  if (!item || before === id || !form.sections.some((s) => s.key === sectionKey)) return form
  const questions = form.questions.filter((q) => q.questionId !== id)
  const index =
    before === undefined
      ? -1
      : questions.findIndex((q) => q.questionId === before && q.sectionKey === sectionKey)
  questions.splice(index < 0 ? questions.length : index, 0, { ...item, sectionKey })
  return { ...form, questions }
}
export function publicationErrors(details: unknown): PublicationError[] {
  return Array.isArray(details)
    ? details.filter(
        (item): item is PublicationError =>
          !!item &&
          typeof item === 'object' &&
          typeof item.code === 'string' &&
          typeof item.message === 'string',
      )
    : []
}
export function failureMessage(caught: unknown) {
  const error = toApiError(caught)
  const labels: Record<string, string> = {
    EDIT_CONFLICT: 'Đề vừa được người khác sửa, tải lại',
    EXAM_CODE_TAKEN: 'Mã đề đã tồn tại. Chọn mã khác.',
    EXAM_NOT_EDITABLE: 'Đề không còn ở trạng thái nháp. Tải lại để xem trạng thái mới.',
    EXAM_IN_USE: 'Đề đang được cuộc thi sử dụng, chưa thể lưu trữ.',
    EXAM_NOT_FOUND: 'Không tìm thấy đề thi. Tải lại danh sách.',
    QUESTION_NOT_FOUND: 'Một câu hỏi không còn tồn tại. Tải lại trước khi tiếp tục.',
  }
  return labels[error.errorCode] ?? error.message
}
