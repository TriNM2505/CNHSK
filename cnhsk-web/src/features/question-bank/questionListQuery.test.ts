import { describe, expect, it } from 'vitest'

import { parseQuestionListSearchParams, questionListSearchParams } from './questionListQuery'

describe('question bank source filter', () => {
  it('keeps a known source and drops an unknown one', () => {
    expect(parseQuestionListSearchParams(new URLSearchParams('source=AI')).query.source).toBe('AI')
    const unknown = parseQuestionListSearchParams(new URLSearchParams('source=ROBOT'))
    expect(unknown.query.source).toBe('')
    expect(unknown.needsCleanup).toBe(true)
  })

  it('writes the source into the address only when one is chosen', () => {
    const base = { hskLevel: '', qtype: '', status: '', kid: '', q: '', page: 0 } as const
    expect(questionListSearchParams({ ...base, source: 'AI' }).get('source')).toBe('AI')
    expect(questionListSearchParams({ ...base, source: '' }).has('source')).toBe(false)
    expect(questionListSearchParams(base).has('source')).toBe(false)
  })
})
