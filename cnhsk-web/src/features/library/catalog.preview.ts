import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'

import type {
  DeckDetail,
  DeckSummary,
  LexemeDetail,
  LexemeKind,
  LexemeSummary,
  Note,
  NotePage,
  RadicalCount,
  SearchResult,
  TranslateResult,
  TrashNote,
} from './types'

// Original demo content; no exam questions or knowledge-tree source data.
const entries: readonly [number, LexemeKind, string, string, string, number][] = [
  [101, 'CHAR', '日', 'rì', 'ngày; mặt trời', 1],
  [102, 'CHAR', '月', 'yuè', 'tháng; mặt trăng', 1],
  [103, 'CHAR', '人', 'rén', 'người', 1],
  [104, 'CHAR', '山', 'shān', 'núi', 2],
  [105, 'CHAR', '水', 'shuǐ', 'nước', 1],
  [106, 'CHAR', '火', 'huǒ', 'lửa', 3],
  [107, 'CHAR', '木', 'mù', 'gỗ; cây', 3],
  [108, 'CHAR', '口', 'kǒu', 'miệng', 2],
  [109, 'CHAR', '大', 'dà', 'to; lớn', 1],
  [110, 'CHAR', '小', 'xiǎo', 'nhỏ', 1],
  [111, 'CHAR', '上', 'shàng', 'trên', 1],
  [112, 'CHAR', '下', 'xià', 'dưới', 1],
  [201, 'WORD', '今天', 'jīntiān', 'hôm nay', 1],
  [202, 'WORD', '明天', 'míngtiān', 'ngày mai', 1],
  [203, 'WORD', '朋友', 'péngyou', 'bạn bè', 1],
  [204, 'WORD', '学习', 'xuéxí', 'học tập', 1],
  [205, 'WORD', '中文', 'Zhōngwén', 'tiếng Trung', 2],
  [206, 'WORD', '老师', 'lǎoshī', 'giáo viên', 1],
  [207, 'WORD', '学校', 'xuéxiào', 'trường học', 1],
  [208, 'WORD', '早上', 'zǎoshang', 'buổi sáng', 2],
  [209, 'WORD', '下午', 'xiàwǔ', 'buổi chiều', 1],
  [210, 'WORD', '喝水', 'hē shuǐ', 'uống nước', 1],
  [211, 'WORD', '看书', 'kàn shū', 'đọc sách', 1],
  [212, 'WORD', '生日', 'shēngrì', 'sinh nhật', 2],
  [301, 'GRAMMAR', '是', 'shì', 'là; dùng để giới thiệu', 1],
  [302, 'GRAMMAR', '不', 'bù', 'không; phủ định hành động', 1],
  [303, 'GRAMMAR', '有', 'yǒu', 'có; diễn đạt sự sở hữu', 1],
  [304, 'GRAMMAR', '在', 'zài', 'ở; diễn đạt vị trí', 1],
  [305, 'GRAMMAR', '的', 'de', 'trợ từ nối quan hệ sở hữu', 1],
  [306, 'GRAMMAR', '了', 'le', 'trợ từ chỉ sự thay đổi', 2],
]

export const libraryLexemes: LexemeSummary[] = entries.map(
  ([id, kind, text, pinyin, meaningVi, hskLevel]) => ({
    id,
    kind,
    text,
    pinyin,
    sinoViet: null,
    meaningVi,
    hskLevel,
    approximate: false,
  }),
)

const characterInfo: Record<string, { radical: string; strokeCount: number }> = {
  日: { radical: '日', strokeCount: 4 },
  月: { radical: '月', strokeCount: 4 },
  人: { radical: '人', strokeCount: 2 },
  山: { radical: '山', strokeCount: 3 },
  水: { radical: '水', strokeCount: 4 },
  火: { radical: '火', strokeCount: 4 },
  木: { radical: '木', strokeCount: 4 },
  口: { radical: '口', strokeCount: 3 },
  大: { radical: '大', strokeCount: 3 },
  小: { radical: '小', strokeCount: 3 },
  上: { radical: '一', strokeCount: 3 },
  下: { radical: '一', strokeCount: 3 },
}

const grammarInfo: Record<string, Pick<LexemeDetail, 'structure' | 'examples'>> = {
  是: {
    structure: 'Chủ ngữ + 是 + danh từ',
    examples: [
      { cn: '她是我的老师。', pinyin: 'Tā shì wǒ de lǎoshī.', vi: 'Cô ấy là giáo viên của tôi.' },
    ],
  },
  不: {
    structure: 'Chủ ngữ + 不 + động từ',
    examples: [
      { cn: '今天我不看书。', pinyin: 'Jīntiān wǒ bù kàn shū.', vi: 'Hôm nay tôi không đọc sách.' },
    ],
  },
  有: {
    structure: 'Chủ ngữ + 有 + danh từ',
    examples: [
      { cn: '我有三个朋友。', pinyin: 'Wǒ yǒu sān ge péngyou.', vi: 'Tôi có ba người bạn.' },
    ],
  },
  在: {
    structure: 'Chủ ngữ + 在 + địa điểm',
    examples: [{ cn: '老师在学校。', pinyin: 'Lǎoshī zài xuéxiào.', vi: 'Giáo viên ở trường.' }],
  },
  的: {
    structure: 'Người sở hữu + 的 + danh từ',
    examples: [
      { cn: '这是朋友的书。', pinyin: 'Zhè shì péngyou de shū.', vi: 'Đây là sách của bạn.' },
    ],
  },
  了: {
    structure: 'Câu diễn đạt trạng thái mới + 了',
    examples: [{ cn: '现在下雨了。', pinyin: 'Xiànzài xià yǔ le.', vi: 'Bây giờ trời mưa rồi.' }],
  },
}

export const libraryLexemeDetails: LexemeDetail[] = libraryLexemes.map((entry) => {
  const characters = libraryLexemes.filter((item) => item.kind === 'CHAR')
  const ref = ({ id, text, pinyin, meaningVi }: LexemeSummary) => ({ id, text, pinyin, meaningVi })
  return {
    id: entry.id,
    kind: entry.kind,
    text: entry.text,
    pinyin: entry.pinyin,
    sinoViet: entry.sinoViet,
    meaningVi: entry.meaningVi,
    hskLevel: entry.hskLevel,
    audioUrl: null,
    radical: characterInfo[entry.text]?.radical ?? null,
    strokeCount: characterInfo[entry.text]?.strokeCount ?? null,
    strokeData: null,
    structure: grammarInfo[entry.text]?.structure ?? null,
    examples: grammarInfo[entry.text]?.examples ?? [],
    components:
      entry.kind === 'WORD'
        ? characters.filter((item) => entry.text.includes(item.text)).map(ref)
        : [],
    containedIn:
      entry.kind === 'CHAR'
        ? libraryLexemes
            .filter((item) => item.kind === 'WORD' && item.text.includes(entry.text))
            .map(ref)
        : [],
    related: [],
  }
})

export const librarySearchResult: SearchResult = {
  items: libraryLexemes,
  total: libraryLexemes.length,
}
export const libraryEmptySearch: SearchResult = { items: [], total: 0 }
export const libraryTranslationHint: SearchResult = { ...libraryEmptySearch, hint: 'USE_TRANSLATE' }
export const libraryRadicals: RadicalCount[] = Object.values(characterInfo)
  .reduce<RadicalCount[]>((counts, { radical }) => {
    const existing = counts.find((item) => item.radical === radical)
    if (existing) existing.charCount += 1
    else counts.push({ radical, charCount: 1 })
    return counts
  }, [])
  .sort((left, right) => right.charCount - left.charCount)

export const libraryNotes: Note[] = [
  {
    id: 401,
    title: 'Nhớ từ chỉ thời gian',
    content: '今天 — hôm nay; 明天 — ngày mai. Tự đặt một câu trước buổi học.',
    tags: ['hsk1', 'thời gian'],
    lexemeId: 201,
    sourceType: 'DICTIONARY',
    createdAt: '2026-11-01T02:00:00Z',
    updatedAt: '2026-11-02T03:00:00Z',
  },
  {
    id: 402,
    title: 'Ghi chú tự học',
    content: 'Mỗi buổi chọn hai từ rồi tự viết một đoạn ngắn.',
    tags: ['tự học'],
    lexemeId: null,
    sourceType: 'MANUAL',
    createdAt: '2026-11-01T03:00:00Z',
    updatedAt: '2026-11-01T03:00:00Z',
  },
]

export const libraryNotePage: NotePage = {
  items: libraryNotes.map(({ id, title, content, tags, lexemeId, sourceType, updatedAt }) => ({
    id,
    title,
    tags,
    lexemeId,
    sourceType,
    updatedAt,
    snippet: content.slice(0, 200),
  })),
  total: libraryNotes.length,
}

export const libraryTrash: TrashNote[] = [
  {
    id: 403,
    title: 'Câu cần viết lại',
    snippet: 'Buổi chiều tôi đọc sách cùng bạn.',
    tags: ['luyện câu'],
    lexemeId: null,
    sourceType: 'MANUAL',
    updatedAt: '2026-11-01T04:00:00Z',
    deletedAt: '2026-11-02T04:00:00Z',
    restorableUntil: '2026-12-02T04:00:00Z',
  },
]

export const libraryDecks: DeckDetail[] = [
  {
    id: 501,
    name: 'Từ dùng mỗi ngày',
    description: 'Bộ mẫu từ chủ đề tự soạn.',
    cardCount: 2,
    dueCount: 2,
    createdAt: '2026-11-01T05:00:00Z',
    cards: [
      {
        id: 601,
        lexemeId: 201,
        front: '今天',
        back: 'jīntiān — hôm nay',
        dueAt: null,
        reviewCount: 0,
        lapseCount: 0,
      },
      {
        id: 602,
        lexemeId: 202,
        front: '明天',
        back: 'míngtiān — ngày mai',
        dueAt: '2026-11-02T05:00:00Z',
        reviewCount: 1,
        lapseCount: 0,
      },
    ],
  },
  {
    id: 502,
    name: 'Câu tự đặt',
    description: 'Bộ trống được thêm thẻ thủ công.',
    cardCount: 1,
    dueCount: 0,
    createdAt: '2026-11-01T06:00:00Z',
    cards: [
      {
        id: 603,
        lexemeId: null,
        front: 'Buổi sáng tôi học tiếng Trung.',
        back: '早上我学习中文。',
        dueAt: '2026-11-06T06:00:00Z',
        reviewCount: 2,
        lapseCount: 0,
      },
    ],
  },
]

export const libraryDeckSummaries: DeckSummary[] = libraryDecks.map(
  ({ id, name, description, cardCount, dueCount, createdAt }) => ({
    id,
    name,
    description,
    cardCount,
    dueCount,
    createdAt,
  }),
)

export const libraryTranslation: TranslateResult = {
  translated: '今天我学习中文。',
  pinyin: 'jīntiān wǒ xuéxí Zhōngwén',
  segments: [
    { text: '今天', pinyin: 'jīntiān', lexemeId: 201 },
    { text: '我', pinyin: 'wǒ', lexemeId: null },
    { text: '学习', pinyin: 'xuéxí', lexemeId: 204 },
    { text: '中文', pinyin: 'Zhōngwén', lexemeId: 205 },
    { text: '。', pinyin: null, lexemeId: null },
  ],
  cached: false,
  consumption: 'QUOTA',
}

export const libraryCachedTranslation: TranslateResult = {
  ...libraryTranslation,
  cached: true,
  consumption: null,
}

function samplePreview(title: string, rows: readonly { id: number; text: string }[]) {
  return createElement(
    'section',
    { className: 'mx-auto max-w-reading space-y-4' },
    createElement('h1', { className: 'text-h1-sm text-ink' }, title),
    createElement(
      'ul',
      { className: 'space-y-3' },
      rows.map(({ id, text }) =>
        createElement(
          'li',
          { className: 'rounded-md border border-line bg-surface p-4 text-body text-ink', key: id },
          text,
        ),
      ),
    ),
  )
}

export const previews = [
  {
    group: 'screen',
    id: 'library-dictionary-samples',
    scr: 'SCR-024',
    title: 'Tra cứu — Dữ liệu mẫu',
    render: () =>
      samplePreview(
        'Mục từ mẫu',
        libraryLexemes.map((entry) => ({
          id: entry.id,
          text: `${entry.text} · ${entry.pinyin} · ${entry.meaningVi} · HSK ${entry.hskLevel}`,
        })),
      ),
  },
  {
    group: 'screen',
    id: 'library-detail-samples',
    scr: 'SCR-025',
    title: 'Chi tiết mục từ — Dữ liệu mẫu',
    render: () =>
      samplePreview(
        'Các loại mục từ',
        libraryLexemeDetails
          .filter(({ id }) => [101, 212, 301].includes(id))
          .map((entry) => ({
            id: entry.id,
            text: `${entry.kind} · ${entry.text} · ${entry.meaningVi}${entry.structure ? ` · ${entry.structure}` : ''}`,
          })),
      ),
  },
  {
    group: 'screen',
    id: 'library-translation-samples',
    scr: 'SCR-026',
    title: 'Dịch câu — Dữ liệu mẫu',
    render: () =>
      samplePreview('Bản dịch mẫu', [
        { id: 1, text: 'Hôm nay tôi học tiếng Trung.' },
        { id: 2, text: libraryTranslation.translated },
        { id: 3, text: libraryTranslation.pinyin ?? '' },
      ]),
  },
  {
    group: 'screen',
    id: 'library-note-samples',
    scr: 'SCR-027',
    title: 'Sổ tay — Dữ liệu mẫu',
    render: () =>
      samplePreview(
        'Ghi chú mẫu',
        libraryNotes.map((note) => ({ id: note.id, text: `${note.title}: ${note.content}` })),
      ),
  },
  {
    group: 'screen',
    id: 'library-deck-samples',
    scr: 'SCR-028',
    title: 'Bộ thẻ — Dữ liệu mẫu',
    render: () =>
      samplePreview(
        'Bộ thẻ mẫu',
        libraryDeckSummaries.map((deck) => ({
          id: deck.id,
          text: `${deck.name} · ${deck.cardCount} thẻ · ${deck.dueCount} đến hạn`,
        })),
      ),
  },
  {
    group: 'screen',
    id: 'library-card-samples',
    scr: 'SCR-029',
    title: 'Thẻ — Dữ liệu mẫu',
    render: () =>
      samplePreview(
        'Thẻ mẫu',
        libraryDecks.flatMap((deck) =>
          deck.cards.map((card) => ({ id: card.id, text: `${card.front} · ${card.back}` })),
        ),
      ),
  },
] satisfies readonly PreviewEntry[]
