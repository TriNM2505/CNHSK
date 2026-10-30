import { describe, expect, it } from 'vitest'
import { makeReportDecision } from './moderation-actions'

describe('report decision payloads', () => {
  it.each(['POST', 'COMMENT'] as const)('FR048_omits stale comment action when rejecting %s', (kind) => {
    expect(makeReportDecision(kind, 'REJECT', '  Giữ nguyên\n中文  ', 'DELETE')).toEqual({ decision: 'REJECT', note: 'Giữ nguyên\n中文' })
  })
  it('FR048_omits any comment action for a post action', () => {
    expect(makeReportDecision('POST', 'ACTION', 'Ẩn bài', 'HIDE')).toEqual({ decision: 'ACTION', note: 'Ẩn bài' })
  })
  it.each(['HIDE', 'DELETE'] as const)('FR048_requires an explicit %s action for a comment', (commentAction) => {
    expect(makeReportDecision('COMMENT', 'ACTION', '  Lý do  ', commentAction)).toEqual({ decision: 'ACTION', note: 'Lý do', commentAction })
  })
  it.each(['', 'RESTORE', 'null', 'BAN'])('FR048_rejects unsupported comment choice %s', (choice) => {
    expect(makeReportDecision('COMMENT', 'ACTION', 'Lý do', choice)).toBeUndefined()
  })
  it.each(['', ' ', 'a'.repeat(1001)])('FR048_rejects empty or oversized notes', (note) => {
    expect(makeReportDecision('POST', 'ACTION', note, '')).toBeUndefined()
  })
  it('FR048_permits exactly 1000 trimmed characters and rejects non-decisions', () => {
    expect(makeReportDecision('POST', 'REJECT', ` ${'a'.repeat(1000)} `, '')?.note).toHaveLength(1000)
    expect(makeReportDecision('POST', 'BAN_USER', 'Lý do', '')).toBeUndefined()
  })
})
