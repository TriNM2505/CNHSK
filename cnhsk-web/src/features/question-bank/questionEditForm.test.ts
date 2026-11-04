import { describe, expect, it } from 'vitest'

import {
  limitQuestionInput,
  parseQuestionId,
  questionTextLength,
  questionEditServerErrors,
  questionEditValues,
  toQuestionUpdate,
  validateQuestionEdit,
} from './questionEditForm'
import type { KnowledgeNode, QuestionDetail, QuestionType } from './types'

const nodes: KnowledgeNode[] = ['NP1-901', 'TV2-901', 'CH1-901'].map((kid) => ({
  kid,
  domain: kid.slice(0, 2) as KnowledgeNode['domain'],
  hskLevel: Number(kid[2]),
  nameVi: `Nút tự soạn ${kid}`,
  nameZh: '示例',
  prereqKids: [],
  supportKids: [],
}))

function question(qtype: QuestionType = 'MULTIPLE_CHOICE'): QuestionDetail {
  return {
    id: 901,
    code: 'TEST_901',
    qtype,
    hskLevel: 1,
    difficulty: 2,
    stem: qtype === 'FILL_BLANK' ? '我喝___。' : 'Nội dung câu hỏi tự soạn.',
    options:
      qtype === 'FILL_BLANK'
        ? []
        : qtype === 'SENTENCE_ORDER'
          ? [
              { key: 'A', text: '我', isCorrect: false },
              { key: 'B', text: '喝', isCorrect: false },
              { key: 'C', text: '水', isCorrect: false },
            ]
          : [
              { key: 'A', text: qtype === 'TRUE_FALSE' ? 'Đúng' : 'nước', isCorrect: true },
              { key: 'B', text: qtype === 'TRUE_FALSE' ? 'Sai' : 'sách', isCorrect: false },
            ],
    correctKey: qtype === 'FILL_BLANK' ? '水' : qtype === 'SENTENCE_ORDER' ? 'ABC' : null,
    explanation: 'Lời giải tự soạn.',
    imageUrl: qtype === 'IMAGE_MATCH' ? 'https://assets.example.test/image.svg' : null,
    source: 'IMPORT',
    status: 'APPROVED',
    used: false,
    labels: [{ kid: 'NP1-901', nameVi: 'Câu tự soạn', role: 'PRIMARY' }],
  }
}

describe('question edit form (FR-019…FR-022 FE · AT-27)', () => {
  it.each(['𠀀', '😀'])(
    'FR021_AT27_counts and limits %s without splitting a code point',
    (character) => {
      expect(questionTextLength(character.repeat(50))).toBe(50)
      expect(limitQuestionInput(character.repeat(51), 50)).toBe(character.repeat(50))
      expect(limitQuestionInput(`${character}a`, 2)).toBe(`${character}a`)
    },
  )

  it('FR021_AT27_preserves decomposed input that fits after NFC normalization', () => {
    const value = 'e\u0301'.repeat(50)
    expect(limitQuestionInput(value, 50)).toBe(value)
    expect(limitQuestionInput(value + '𠀀', 50)).toBe('é'.repeat(50))
  })

  it.each([
    ['stem', 2000],
    ['explanation', 3000],
    ['correctKey', 50],
  ] as const)('FR021_AT27_checks %s after NFC normalization in code points', (field, maximum) => {
    const values = questionEditValues(question('FILL_BLANK'))
    const prefix = field === 'stem' ? '___' : ''
    const value = prefix + '𠀀'.repeat(maximum - questionTextLength(prefix))
    expect(validateQuestionEdit({ ...values, [field]: value }, 'FILL_BLANK', nodes)).toEqual({})
    expect(
      validateQuestionEdit({ ...values, [field]: value + '𠀀' }, 'FILL_BLANK', nodes),
    ).toHaveProperty(field)
    if (field !== 'stem')
      expect(
        validateQuestionEdit(
          { ...values, [field]: 'e\u0301'.repeat(maximum) },
          'FILL_BLANK',
          nodes,
        ),
      ).toEqual({})
  })

  it('FR021_AT27_checks option and error note boundaries in code points', () => {
    const values = questionEditValues(question())
    values.options[1] = {
      key: 'B',
      text: '𠀀'.repeat(300),
      isCorrect: false,
      errorKid: 'TV2-901',
      errorNote: '😀'.repeat(200),
    }
    expect(validateQuestionEdit(values, 'MULTIPLE_CHOICE', nodes)).toEqual({})
    values.options[1]!.text += '𠀀'
    values.options[1]!.errorNote += '😀'
    expect(validateQuestionEdit(values, 'MULTIPLE_CHOICE', nodes)).toMatchObject({
      'options[1].text': expect.any(String),
      'options[1].errorNote': expect.any(String),
    })
  })

  it('FR021_AT27_normalizes label and error KIDs before membership and duplicate checks', () => {
    const values = questionEditValues(question())
    values.primaryKid = ' np1-901 '
    values.secondaryKids = [' tv2-901 ']
    values.options[1]!.errorKid = ' ch1-901 '
    expect(toQuestionUpdate(values)).toMatchObject({
      primaryKid: 'NP1-901',
      secondaryKids: ['TV2-901'],
      options: [expect.anything(), expect.objectContaining({ errorKid: 'CH1-901' })],
    })
    expect(validateQuestionEdit(values, 'MULTIPLE_CHOICE', nodes)).toEqual({})
    values.secondaryKids = ['NP1-901', ' np1-901 ']
    expect(validateQuestionEdit(values, 'MULTIPLE_CHOICE', nodes)).toHaveProperty('secondaryKids')
  })

  it.each(['我  学习', '我\t学习', '我\u00a0学习', '我\u2007学习', 'cafe\u0301'])(
    'FR021_AT27_rejects normalized duplicate sentence fragments %s',
    (text) => {
      const values = questionEditValues(question('SENTENCE_ORDER'))
      values.options[0]!.text = text.startsWith('我') ? '我 学习' : 'café'
      values.options[1]!.text = text
      expect(validateQuestionEdit(values, 'SENTENCE_ORDER', nodes)).toHaveProperty('options')
    },
  )

  it.each(['HTTP://example.test/a', 'HTTPS://example.test/a', 'https://example.test/a\u2007b'])(
    'FR021_AT27_rejects URL %s exactly as QuestionRules',
    (audioUrl) => {
      expect(
        validateQuestionEdit(
          { ...questionEditValues(question()), audioUrl },
          'MULTIPLE_CHOICE',
          nodes,
        ),
      ).toHaveProperty('audioUrl')
    },
  )

  it('FR021_AT27_checks the backend URL prefix and 500 code point boundary', () => {
    const values = questionEditValues(question())
    const audioUrl = 'https://' + '𠀀'.repeat(492)
    expect(validateQuestionEdit({ ...values, audioUrl }, 'MULTIPLE_CHOICE', nodes)).toEqual({})
    expect(
      validateQuestionEdit({ ...values, audioUrl: audioUrl + '𠀀' }, 'MULTIPLE_CHOICE', nodes),
    ).toHaveProperty('audioUrl')
    expect(
      validateQuestionEdit({ ...values, audioUrl: 'http://' }, 'MULTIPLE_CHOICE', nodes),
    ).toEqual({})
  })

  it('FR021_AT27_keeps repeated and unknown server details instead of dropping them', () => {
    expect(
      questionEditServerErrors([
        { field: 'stem', message: 'Lỗi thứ nhất.' },
        { field: 'stem', message: 'Lỗi thứ hai.' },
        { field: 'newServerField', message: 'Lỗi chưa có ô tương ứng.' },
      ]),
    ).toEqual({ stem: 'Lỗi thứ nhất.\nLỗi thứ hai.', newServerField: 'Lỗi chưa có ô tương ứng.' })
  })

  it.each([
    'MULTIPLE_CHOICE',
    'TRUE_FALSE',
    'IMAGE_MATCH',
    'SENTENCE_MATCH',
    'FILL_BLANK',
    'SENTENCE_ORDER',
  ] as const)('FR021_AT27_accepts the supported %s structure', (qtype) => {
    expect(validateQuestionEdit(questionEditValues(question(qtype)), qtype, nodes)).toEqual({})
  })

  it.each([undefined, '', '0', '-1', '1.2', 'abc', '9007199254740992'])(
    'rejects invalid id %s',
    (raw) => {
      expect(parseQuestionId(raw)).toBeUndefined()
    },
  )
  it('accepts a positive safe integer id', () => expect(parseQuestionId('901')).toBe(901))

  it('FR020_AT27_builds only writable fields and copies options without changing the source', () => {
    const data = question()
    const values = questionEditValues(data)
    values.options[0]!.text = '  nước mới  '
    values.explanation = '  Lời giải mới.  '
    values.audioUrl = '  '
    values.secondaryKids = ['TV2-901']
    const body = toQuestionUpdate({
      ...values,
      qtype: 'ESSAY',
      status: 'REJECTED',
    } as typeof values)
    expect(body.options[0]!.text).toBe('nước mới')
    expect(body.explanation).toBe('Lời giải mới.')
    expect(body.audioUrl).toBeNull()
    expect(body.secondaryKids).toEqual(['TV2-901'])
    expect(data.options[0]!.text).toBe('nước')
    expect(Object.keys(body).sort()).toEqual(
      [
        'stem',
        'options',
        'correctKey',
        'explanation',
        'hskLevel',
        'difficulty',
        'audioUrl',
        'imageUrl',
        'primaryKid',
        'secondaryKids',
      ].sort(),
    )
  })

  it('FR019_AT27_preserves missing source fields as empty values without fabricating labels', () => {
    expect(questionEditValues({ ...question(), explanation: null, labels: [] })).toMatchObject({
      explanation: '',
      primaryKid: '',
      secondaryKids: [],
      correctKey: null,
      audioUrl: null,
    })
  })

  it.each([
    ['stem', ' '],
    ['stem', 'a'.repeat(2001)],
    ['explanation', ''],
    ['explanation', 'a'.repeat(3001)],
    ['hskLevel', 4],
    ['hskLevel', 9],
    ['difficulty', 0],
    ['primaryKid', ''],
    ['primaryKid', 'NP1-999'],
    ['audioUrl', 'javascript:alert(1)'],
    ['imageUrl', 'https://example.test/a b'],
    ['audioUrl', `https://example.test/${'a'.repeat(500)}`],
    ['secondaryKids', ['NP1-901']],
    ['secondaryKids', ['TV2-901', 'TV2-901']],
    ['secondaryKids', ['TV2-901', 'CH1-901', 'NP1-999']],
    ['secondaryKids', ['NP1-999']],
  ])('FR021_AT27_rejects invalid %s', (field, value) => {
    const values = { ...questionEditValues(question()), [field as string]: value }
    expect(validateQuestionEdit(values, 'MULTIPLE_CHOICE', nodes)[field as string]).toBeTruthy()
  })

  it.each([
    [
      { key: 'A', text: 'nước', isCorrect: true },
      { key: 'B', text: 'sách', isCorrect: true },
    ],
    [
      { key: 'A', text: 'nước', isCorrect: false },
      { key: 'B', text: 'sách', isCorrect: false },
    ],
    [
      { key: 'A', text: 'nước', isCorrect: true },
      { key: 'C', text: 'sách', isCorrect: false },
    ],
    [{ key: 'A', text: 'nước', isCorrect: true }],
    [
      { key: 'A', text: ' nước ', isCorrect: true },
      { key: 'B', text: 'nước', isCorrect: false },
    ],
  ])('FR021_AT27_rejects malformed options %#', (...options) => {
    expect(
      validateQuestionEdit({ ...questionEditValues(question()), options }, 'MULTIPLE_CHOICE', nodes)
        .options,
    ).toBeTruthy()
  })

  it('FR021_AT27_checks distractor labels and option text bounds', () => {
    const values = questionEditValues(question())
    values.options[0] = { key: 'A', text: '', isCorrect: true, errorKid: 'TV2-901' }
    values.options[1] = {
      key: 'B',
      text: 'a'.repeat(301),
      isCorrect: false,
      errorNote: 'Thiếu nút lỗi.',
    }
    expect(validateQuestionEdit(values, 'MULTIPLE_CHOICE', nodes)).toMatchObject({
      'options[0].text': expect.any(String),
      'options[0].errorKid': expect.any(String),
      'options[1].text': expect.any(String),
      'options[1].errorNote': expect.any(String),
    })
    values.options[1] = { key: 'B', text: 'sách', isCorrect: false, errorKid: 'NP1-999' }
    expect(
      validateQuestionEdit(values, 'MULTIPLE_CHOICE', nodes)['options[1].errorKid'],
    ).toBeTruthy()
    values.options[1]!.errorKid = 'TV2-901'
    values.options[1]!.errorNote = 'a'.repeat(201)
    expect(
      validateQuestionEdit(values, 'MULTIPLE_CHOICE', nodes)['options[1].errorNote'],
    ).toBeTruthy()
  })

  it('FR021_AT27_checks type-specific fill, order, true/false and image constraints', () => {
    const fill = questionEditValues(question('FILL_BLANK'))
    expect(
      validateQuestionEdit(
        { ...fill, stem: 'Không có ô trống', correctKey: '' },
        'FILL_BLANK',
        nodes,
      ),
    ).toHaveProperty('correctKey')
    expect(
      validateQuestionEdit({ ...fill, stem: 'Không có ô trống' }, 'FILL_BLANK', nodes),
    ).toHaveProperty('stem')
    expect(
      validateQuestionEdit({ ...fill, options: question().options }, 'FILL_BLANK', nodes),
    ).toHaveProperty('options')
    const order = questionEditValues(question('SENTENCE_ORDER'))
    expect(
      validateQuestionEdit({ ...order, correctKey: 'AAB' }, 'SENTENCE_ORDER', nodes),
    ).toHaveProperty('correctKey')
    expect(
      validateQuestionEdit(
        { ...order, options: order.options.slice(0, 2) },
        'SENTENCE_ORDER',
        nodes,
      ),
    ).toHaveProperty('options')
    expect(
      validateQuestionEdit(questionEditValues(question()), 'TRUE_FALSE', nodes),
    ).toHaveProperty('options')
    expect(
      validateQuestionEdit(questionEditValues(question()), 'IMAGE_MATCH', nodes),
    ).toHaveProperty('imageUrl')
    expect(
      validateQuestionEdit(questionEditValues(question('ESSAY')), 'ESSAY', nodes),
    ).toHaveProperty('qtype')
  })

  it('FR021_AT27_reads safe server field errors and ignores malformed details', () => {
    expect(questionEditServerErrors(null)).toEqual({})
    expect(
      questionEditServerErrors([
        null,
        'bad',
        { field: 1, message: 'bad' },
        { field: 'primaryKid', message: 'Nút không tồn tại.' },
      ]),
    ).toEqual({ primaryKid: 'Nút không tồn tại.' })
  })
})
