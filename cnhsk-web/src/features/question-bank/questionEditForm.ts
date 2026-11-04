import { SUPPORTED_HSK_LEVELS } from '@/shared/ui/LevelBadge'

import type { KnowledgeNode, QuestionDetail, QuestionType, QuestionUpdate } from './types'

export type QuestionEditErrors = Record<string, string>

export function parseQuestionId(value: string | undefined): number | undefined {
  if (!value || !/^\d+$/.test(value)) return undefined
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : undefined
}

export function questionEditValues(question: QuestionDetail): QuestionUpdate {
  return {
    stem: question.stem,
    options: question.options.map((option) => ({ ...option })),
    correctKey: question.correctKey ?? null,
    explanation: question.explanation ?? '',
    hskLevel: question.hskLevel,
    difficulty: question.difficulty,
    audioUrl: question.audioUrl ?? null,
    imageUrl: question.imageUrl ?? null,
    primaryKid: question.labels.find(({ role }) => role === 'PRIMARY')?.kid ?? '',
    secondaryKids: question.labels.filter(({ role }) => role === 'SECONDARY').map(({ kid }) => kid),
  }
}

function normalized(value: string): string {
  return value.normalize('NFC').trim()
}

export function toQuestionUpdate(values: QuestionUpdate): QuestionUpdate {
  return {
    stem: normalized(values.stem),
    options: values.options.map(({ key, text, isCorrect, errorKid, errorNote }) => ({
      key,
      text: normalized(text),
      isCorrect,
      errorKid: errorKid || null,
      errorNote: errorNote ? normalized(errorNote) || null : null,
    })),
    correctKey: values.correctKey ? normalized(values.correctKey) || null : null,
    explanation: normalized(values.explanation),
    hskLevel: values.hskLevel,
    difficulty: values.difficulty,
    audioUrl: values.audioUrl ? normalized(values.audioUrl) || null : null,
    imageUrl: values.imageUrl ? normalized(values.imageUrl) || null : null,
    primaryKid: values.primaryKid,
    secondaryKids: [...values.secondaryKids],
  }
}

export function validateQuestionEdit(
  values: QuestionUpdate,
  qtype: QuestionType,
  nodes: readonly KnowledgeNode[],
): QuestionEditErrors {
  const body = toQuestionUpdate(values)
  const errors: QuestionEditErrors = {}
  const kids = new Set(nodes.map(({ kid }) => kid))
  const choice = ['MULTIPLE_CHOICE', 'IMAGE_MATCH', 'SENTENCE_MATCH'].includes(qtype)
  if (!body.stem || body.stem.length > 2000)
    errors.stem = 'Nhập nội dung câu hỏi từ 1 đến 2.000 ký tự.'
  if (!body.explanation || body.explanation.length > 3000)
    errors.explanation = 'Nhập lời giải từ 1 đến 3.000 ký tự.'
  if (!SUPPORTED_HSK_LEVELS.some((level) => level === body.hskLevel))
    errors.hskLevel = 'Chọn cấp HSK từ 1 đến 3.'
  if (![1, 2, 3].includes(body.difficulty)) errors.difficulty = 'Chọn độ khó từ 1 đến 3.'
  if (!body.primaryKid) errors.primaryKid = 'Chọn một nút năng lực chính.'
  else if (!kids.has(body.primaryKid)) errors.primaryKid = 'Nút chính không có trong cây năng lực.'
  if (body.secondaryKids.length > 2) errors.secondaryKids = 'Chọn tối đa hai nút năng lực phụ.'
  else if (
    new Set(body.secondaryKids).size !== body.secondaryKids.length ||
    body.secondaryKids.includes(body.primaryKid)
  )
    errors.secondaryKids = 'Nút phụ không được trùng nhau hoặc trùng nút chính.'
  else if (body.secondaryKids.some((kid) => !kids.has(kid)))
    errors.secondaryKids = 'Nút phụ không có trong cây năng lực.'
  for (const field of ['audioUrl', 'imageUrl'] as const) {
    const value = body[field]
    if (value && (value.length > 500 || !/^https?:\/\/[^\s]+$/i.test(value)))
      errors[field] = 'Dùng URL http:// hoặc https://, tối đa 500 ký tự, không có khoảng trắng.'
  }
  if (qtype === 'IMAGE_MATCH' && !body.imageUrl)
    errors.imageUrl = 'Câu ghép hình cần đường dẫn ảnh.'
  if (qtype === 'ESSAY') errors.qtype = 'Chưa hỗ trợ sửa câu tự luận.'
  const options = body.options
  const minimum = qtype === 'SENTENCE_ORDER' ? 3 : 2
  if (qtype === 'FILL_BLANK') {
    if (!body.stem.includes('___')) errors.stem = 'Câu điền chỗ trống phải có ___.'
    if (options.length) errors.options = 'Câu điền chỗ trống không có phương án.'
    if (!body.correctKey || body.correctKey.length > 50)
      errors.correctKey = 'Nhập đáp án từ 1 đến 50 ký tự.'
  } else if (qtype !== 'ESSAY') {
    if (options.length < minimum || options.length > 6)
      errors.options = `Câu hỏi cần từ ${minimum} đến 6 phương án.`
    if (options.some(({ key }, index) => key !== 'ABCDEF'[index]))
      errors.options = 'Khoá phương án phải liên tiếp từ A.'
    const texts = options.map(({ text }) => text.replace(/\s+/g, ' '))
    if (new Set(texts).size !== texts.length)
      errors.options = 'Nội dung phương án không được trùng.'
    if (
      qtype === 'TRUE_FALSE' &&
      (options.length !== 2 || options[0]?.text !== 'Đúng' || options[1]?.text !== 'Sai')
    )
      errors.options = 'Câu đúng/sai dùng A: Đúng và B: Sai.'
    if (qtype === 'SENTENCE_ORDER') {
      const keys = options
        .map(({ key }) => key)
        .sort()
        .join('')
      if (!body.correctKey || body.correctKey.split('').sort().join('') !== keys)
        errors.correctKey = 'Thứ tự đúng phải dùng mỗi khoá phương án đúng một lần.'
      if (options.some(({ isCorrect }) => isCorrect))
        errors.options = 'Mảnh câu không có phương án đánh dấu đúng.'
    } else if (options.filter(({ isCorrect }) => isCorrect).length !== 1)
      errors.options = 'Câu hỏi phải có đúng một đáp án đúng.'
  }
  for (const [index, option] of options.entries()) {
    if (!option.text || option.text.length > 300)
      errors[`options[${index}].text`] = 'Nhập phương án từ 1 đến 300 ký tự.'
    if (option.errorKid && (!choice || option.isCorrect || !kids.has(option.errorKid)))
      errors[`options[${index}].errorKid`] =
        'Chỉ gắn nút lỗi có trong cây cho phương án sai của câu chọn đáp án.'
    if (option.errorNote && (!option.errorKid || option.errorNote.length > 200))
      errors[`options[${index}].errorNote`] = 'Ghi chú lỗi cần nút lỗi và tối đa 200 ký tự.'
  }
  return errors
}

export function questionEditServerErrors(details: unknown): QuestionEditErrors {
  const errors: QuestionEditErrors = {}
  if (!Array.isArray(details)) return errors
  for (const detail of details) {
    if (typeof detail !== 'object' || detail === null) continue
    if (
      'field' in detail &&
      'message' in detail &&
      typeof detail.field === 'string' &&
      typeof detail.message === 'string'
    )
      errors[detail.field] = detail.message
  }
  return errors
}
