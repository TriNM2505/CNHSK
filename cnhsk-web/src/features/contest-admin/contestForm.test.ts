import { describe, expect, it } from 'vitest'
import { contestFixture } from './test-fixtures'
import { draftOf, validateDraft, vietnamInput, vietnamIso, writeOf } from './contestForm'

describe('Vietnam contest form', () => {
  it('converts offsets and calendar boundaries using UTC+7 independently of local timezone', () => {
    expect(vietnamInput('2030-12-31T20:30:00Z')).toBe('2031-01-01T03:30')
    expect(vietnamInput('2030-12-06T04:00:00-08:00')).toBe('2030-12-06T19:00')
    expect(vietnamIso('2030-12-06T19:00')).toBe('2030-12-06T19:00:00+07:00')
    expect(Date.parse(vietnamIso('2030-12-06T19:00')!)).toBe(Date.parse('2030-12-06T12:00:00Z'))
  })
  it.each([
    '2030-02-29T19:00',
    '2030-02-30T19:00',
    '2030-12-06T24:00',
    '2030-13-06T19:00',
    '',
    '2030-12-06T19:00Z',
  ])('rejects invalid local calendar input %s', (value) => {
    expect(vietnamIso(value)).toBeNull()
  })
  it('retains an attached exam and nullable optional values, sends no display or ranking fields', () => {
    const draft = draftOf({ ...contestFixture, description: null, maxParticipants: null })
    expect(validateDraft(draft)).toEqual({})
    expect(writeOf(draft)).toEqual({
      name: contestFixture.name,
      description: null,
      examId: 41,
      startsAt: contestFixture.startsAt,
      endsAt: contestFixture.endsAt,
      maxParticipants: null,
      prizes: contestFixture.prizes,
    })
    expect(draftOf(null)).toMatchObject({ examId: '', prizes: [], startsAt: '' })
  })
  it('validates required fields, range, lengths and integer bounds without adding a prize overlap rule', () => {
    const draft = draftOf(contestFixture)
    expect(
      validateDraft({
        ...draft,
        name: ' ',
        description: 'x'.repeat(5001),
        startsAt: draft.endsAt,
        maxParticipants: '100001',
        examId: '-1',
        prizes: [{ fromRank: '0', toRank: '1.5', description: 'x'.repeat(201) }],
      }),
    ).toMatchObject({
      name: expect.any(String),
      description: expect.any(String),
      endsAt: expect.any(String),
      maxParticipants: expect.any(String),
      examId: expect.any(String),
      'prizes.0': expect.any(String),
    })
    expect(validateDraft({ ...draft, maxParticipants: '1.5' })).toHaveProperty('maxParticipants')
    expect(validateDraft({ ...draft, name: 'x'.repeat(201) })).toHaveProperty('name')
    expect(
      validateDraft({ ...draft, prizes: Array.from({ length: 11 }, () => draft.prizes[0]!) }),
    ).toHaveProperty('prizes')
    expect(
      validateDraft({
        ...draft,
        prizes: [
          { fromRank: '3', toRank: '1', description: '' },
          { fromRank: '1', toRank: '3', description: 'Giải tự soạn' },
        ],
      }),
    ).toEqual({})
  })
})
