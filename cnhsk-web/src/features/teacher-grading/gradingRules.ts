import { codePointLength, trimmedEssay } from '@/features/grading/essayRules'
import type { GradingResultWrite, ResultDraft } from './types'

export const criterionLabels = {
  grammar: 'Ngữ pháp',
  vocabulary: 'Từ vựng',
  coherence: 'Mạch lạc',
  writing: 'Chữ viết',
} as const
export function emptyDraft(): ResultDraft {
  return {
    criteria: { grammar: '', vocabulary: '', coherence: '', writing: '' },
    feedback: '',
    corrections: [],
  }
}
export function totalScore(draft: ResultDraft): number {
  return Object.values(draft.criteria).reduce(
    (total, value) => total + (Number.isFinite(Number(value)) ? Number(value) : 0),
    0,
  )
}
export function resultErrors(draft: ResultDraft): Record<string, string> {
  const errors: Record<string, string> = {}
  for (const [key, value] of Object.entries(draft.criteria)) {
    if (!/^\d+$/.test(value) || Number(value) > 25)
      errors[`criteria.${key}`] = 'Nhập số nguyên từ 0 đến 25.'
  }
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
export function resultPayload(draft: ResultDraft): GradingResultWrite {
  return {
    criteria: {
      grammar: Number(draft.criteria.grammar),
      vocabulary: Number(draft.criteria.vocabulary),
      coherence: Number(draft.criteria.coherence),
      writing: Number(draft.criteria.writing),
    },
    feedback: draft.feedback,
    corrections: draft.corrections.map(({ original, corrected, note }) => ({
      original,
      corrected,
      note: note || null,
    })),
  }
}
