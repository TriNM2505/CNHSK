import { describe, expect, it } from 'vitest'

import {
  parseQuestionId,
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
