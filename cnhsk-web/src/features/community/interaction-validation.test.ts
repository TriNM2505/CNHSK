import { describe, expect, it } from 'vitest'
import { makeReport, validateText } from './interaction-validation'
describe('discussion input', () => {
  it.each([['', false], ['  \n ', false], ['a', true], ['a'.repeat(5000), true], ['a'.repeat(5001), false]])('FR017_comment trim and boundary validation %s', (value, valid) => {
    expect(validateText(value, 5000) === undefined).toBe(valid)
  })
  it('FR005_FR017_preserves internal newline and measures UTF-16 length explicitly', () => {
    expect(validateText('  中文\n学习  ', 5)).toBeUndefined()
    expect('😀'.length).toBe(2)
    expect(validateText('😀'.repeat(2501), 5000)).toBeDefined()
  })
  it('FR032_report OTHER requires real detail and emits only contract fields (AT-30)', () => {
    expect(makeReport('POST', 42, 'OTHER', ' \n ')).toBeUndefined()
    expect(makeReport('COMMENT', 11, 'FAKE', 'x')).toBeUndefined()
    expect(makeReport('POST', 42, 'OTHER', ' x\ny ')).toEqual({ targetType: 'POST', targetId: 42, reason: 'OTHER', detail: 'x\ny' })
    expect(makeReport('COMMENT', 11, 'SPAM', '')).toEqual({ targetType: 'COMMENT', targetId: 11, reason: 'SPAM', detail: null })
    expect(makeReport('POST', 42, 'OTHER', 'x'.repeat(1000))).toBeDefined()
    expect(makeReport('POST', 42, 'OTHER', 'x'.repeat(1001))).toBeUndefined()
  })
  it('FR052_reason boundaries after trim', () => {
    expect(validateText(' ', 1000)).toBeDefined()
    expect(validateText('x'.repeat(1000), 1000)).toBeUndefined()
    expect(validateText('x'.repeat(1001), 1000)).toBeDefined()
  })
})
