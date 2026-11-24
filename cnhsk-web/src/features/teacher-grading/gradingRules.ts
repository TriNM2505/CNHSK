import { codePointLength, trimmedEssay } from '@/features/grading/essayRules'
import type { RubricDefinition } from '@/features/grading/rubrics'
import type { GradingResultWrite, ResultDraft } from './types'

export const MAX_ERROR_CODES = 20

export function emptyDraft(): ResultDraft {
  return { scores: {}, errors: [], feedback: '', corrections: [] }
}

/** Rubric total on the 0–100 scale the learner sees, counting blank criteria as 0. */
export function totalScore(draft: ResultDraft, rubric: RubricDefinition): number {
  const sum = rubric.criteria.reduce((total, criterion) => {
    const value = Number(draft.scores[criterion.code])
    return total + (Number.isFinite(value) ? value : 0)
  }, 0)
  return Math.round((100 * sum) / (rubric.max * rubric.criteria.length))
}

export function resultErrors(draft: ResultDraft, rubric: RubricDefinition): Record<string, string> {
  const errors: Record<string, string> = {}
  for (const criterion of rubric.criteria) {
    const value = draft.scores[criterion.code] ?? ''
    if (!/^\d+$/.test(value) || Number(value) > rubric.max)
      errors[`scores.${criterion.code}`] = `Chọn điểm từ 0 đến ${rubric.max}.`
  }
  if (new Set(draft.errors).size !== draft.errors.length)
    errors.errors = 'Mỗi mã lỗi chỉ gắn một lần.'
  if (draft.errors.length > MAX_ERROR_CODES) errors.errors = `Tối đa ${MAX_ERROR_CODES} mã lỗi.`
  if (!trimmedEssay(draft.feedback) || codePointLength(draft.feedback) > 5000)
    errors.feedback = 'Nhận xét phải có nội dung và tối đa 5.000 ký tự.'
  if (draft.corrections.length > 50) errors.corrections = 'Tối đa 50 dòng sửa lỗi.'
  draft.corrections.forEach((item, index) => {
    for (const key of ['original', 'corrected'] as const) {
      if (!trimmedEssay(item[key]) || codePointLength(item[key]) > 500)
        errors[`corrections.${index}.${key}`] = 'Nhập nội dung, tối đa 500 ký tự.'
    }
    if (codePointLength(item.note) > 500)
      errors[`corrections.${index}.note`] = 'Ghi chú tối đa 500 ký tự.'
  })
  return errors
}

export function resultPayload(draft: ResultDraft, rubric: RubricDefinition): GradingResultWrite {
  return {
    rubric: rubric.code,
    scores: Object.fromEntries(
      rubric.criteria.map((criterion) => [criterion.code, Number(draft.scores[criterion.code])]),
    ),
    errors: draft.errors,
    feedback: draft.feedback,
    corrections: draft.corrections.map(({ original, corrected, note }) => ({
      original,
      corrected,
      note: note || null,
    })),
  }
}
