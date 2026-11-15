import { describe, expect, it } from 'vitest'
import { settingFixture, settingsFixtures } from './test-fixtures'
import { formatNumber, validateSetting } from './settingsForm'
import type { SettingDraft } from './types'

const draft: SettingDraft = {
  key: settingFixture.key,
  mode: 'edit',
  value: '20',
  reason: 'Điều chỉnh.',
  confirming: false,
}
describe('setting validation', () => {
  it.each(['', ' ', '-1', '10001', '1.5', '1e2', 'NaN', 'Infinity', '9007199254740993'])(
    'rejects %s before submitting',
    (value) => {
      expect(
        validateSetting(settingFixture, { ...draft, value }, settingsFixtures).value,
      ).toBeTruthy()
    },
  )
  it.each(['0', '10000'])('accepts inclusive boundary %s', (value) => {
    expect(validateSetting(settingFixture, { ...draft, value }, settingsFixtures)).toEqual({})
  })
  it.each(['', ' \n\t', 'x'.repeat(501), '😀'.repeat(501)])(
    'rejects invalid reason of length %s',
    (reason) => {
      expect(
        validateSetting(settingFixture, { ...draft, reason }, settingsFixtures).reason,
      ).toBeTruthy()
    },
  )
  it('counts reason length by Unicode code points', () => {
    expect(
      validateSetting(settingFixture, { ...draft, reason: '😀'.repeat(500) }, settingsFixtures),
    ).toEqual({})
  })
  it('validates both edits and resets against effective Hanzi limits', () => {
    const min = settingsFixtures.find((row) => row.key === 'grading.min-hanzi')!
    const max = settingsFixtures.find((row) => row.key === 'grading.max-hanzi')!
    expect(validateSetting(min, { ...draft, value: '500' }, settingsFixtures).value).toContain(
      'nhỏ hơn',
    )
    expect(validateSetting(max, { ...draft, value: '100' }, settingsFixtures).value).toContain(
      'nhỏ hơn',
    )
    expect(
      validateSetting({ ...min, defaultValue: 600 }, { ...draft, mode: 'reset' }, settingsFixtures)
        .value,
    ).toContain('nhỏ hơn')
    expect(
      validateSetting({ ...max, defaultValue: 50 }, { ...draft, mode: 'reset' }, settingsFixtures)
        .value,
    ).toContain('nhỏ hơn')
  })
  it('formats the approved number display in Vietnamese', () => {
    expect(formatNumber(1234567)).toBe('1.234.567')
  })
})
