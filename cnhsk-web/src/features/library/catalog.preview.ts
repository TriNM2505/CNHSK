import { createElement, Fragment, useState } from 'react'

import type { PreviewEntry, PreviewState } from '@/app/preview/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { LearnerLayout, PublicLayout } from '@/shared/layout'
import { previewLearner } from '@/shared/layout/Layout.preview'

import { LexemeEntryBody } from './components/LexemeEntryBody'
import { TranslateWorkspaceView } from './components/TranslateWorkspaceView'
import { LexemeDetailView } from './components/LexemeDetailView'
import { LexemeSaveActions, type LexemeSaveActionsProps } from './components/LexemeSaveActions'
import { NoteEditor } from './components/NoteEditor'
import { PersonalNotesView } from './components/PersonalNotesView'
import { PersonalNotesScreenPreview } from './components/PersonalNotesScreen.preview'
import { DeckListView } from './components/DeckListView'
import { DeckListScreenPreview } from './components/DeckListScreen.preview'
import { DeckDetailView } from './components/DeckDetailView'
import { DeckDetailScreenPreview } from './components/DeckDetailScreen.preview'
import { FlashcardReviewView } from './components/FlashcardReviewView'
import {
  SentenceWords,
  TranslationOutput,
  type TranslationPanelState,
} from './components/TranslationResultPanel'
import {
  validateDictionarySearch,
  type DictionarySearch,
} from './dictionarySearch'
import { newNoteDraft, noteDraft, type NoteDraft } from './personalNotes'
import { emptyCardDraft, emptyDeckDraft, type CardDraft } from './flashcardForms'

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
  TranslateRequest,
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

function TranslationPreview({
  state,
  cached = false,
  insufficient = false,
  from = 'VI',
}: {
  state: PreviewState
  cached?: boolean
  insufficient?: boolean
  from?: TranslateRequest['from']
}) {
  const type: DictionarySearch['type'] = from === 'VI' ? 'MEANING' : 'HANZI'
  const [draft, setDraft] = useState<DictionarySearch>({
    type,
    q: state === 'empty' ? '' : from === 'VI' ? 'Hôm nay tôi học tiếng Trung.' : '今天我学习中文。',
    radical: '',
    strokes: '',
    page: 0,
  })
  const [done, setDone] = useState(state !== 'empty')
  const [lexemeId, setLexemeId] = useState<number>()
  const entry = libraryLexemeDetails.find(({ id }) => id === lexemeId)
  const source = draft.type === 'MEANING' ? 'VI' : 'ZH'
  const panel: TranslationPanelState | undefined = !done
    ? undefined
    : state === 'loading'
      ? { state: 'pending' }
      : state === 'error'
        ? {
            state: 'error',
            onRetry: () => undefined,
            error: new ApiError({
              status: insufficient ? 402 : 503,
              errorCode: insufficient ? 'INSUFFICIENT_CREDITS' : 'TRANSLATE_UNAVAILABLE',
              message: insufficient ? 'Bạn đã hết lượt dịch và không đủ điểm.' : 'Chưa thể dịch.',
            }),
          }
        : {
            state: 'ready',
            result: {
              ...(cached ? libraryCachedTranslation : libraryTranslation),
              translated: source === 'VI' ? libraryTranslation.translated : 'Hôm nay tôi học tiếng Trung.',
            },
          }
  const panelElement = panel
    ? createElement(TranslationOutput, { panel, to: source === 'ZH' ? 'VI' : 'ZH' })
    : createElement('p', { className: 'text-h3 text-ink-muted' }, 'Bản dịch')
  return createElement(TranslateWorkspaceView, {
    draft,
    onChange: (next) => {
      setDraft(next)
      setDone(false)
      setLexemeId(undefined)
    },
    onSubmit: () => setDone(true),
    onSwap: () => setDraft({ ...draft, type: draft.type === 'MEANING' ? 'HANZI' : 'MEANING' }),
    output: panelElement,
    details: createElement(
      Fragment,
      null,
      panel?.state === 'ready'
        ? createElement(SentenceWords, {
            segments: panel.result.segments,
            selectedId: lexemeId,
            onOpen: setLexemeId,
          })
        : null,
      entry
        ? createElement(LexemeEntryBody, {
            entry,
            related: { onOpen: setLexemeId },
            saveActions: createElement(LexemeSaveActions, {
              key: entry.id,
              entry,
              access: 'learner',
              loginTo: '/login?redirect=%2Ftranslate',
              onReloadAuth: () => undefined,
              onCreateNote: async () => undefined,
              onLoadDecks: async () => libraryDeckSummaries,
              onAddCard: async () => undefined,
            }),
          })
        : null,
    ),
    listed: false,
    searched: false,
    state: 'ready',
    onRetry: () => undefined,
    page: 0,
    onPageChange: () => undefined,
    onSelect: setLexemeId,
    radicals: libraryRadicals,
    onRadicalsRetry: () => undefined,
    loginTo: '/login?redirect=%2Ftranslate',
    registerTo: '/register?redirect=%2Ftranslate',
    guest: false,
  })
}

function TranslationScreenPreview(props: Parameters<typeof TranslationPreview>[0]) {
  const auth: AuthContextValue = {
    error: null,
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
    status: 'authenticated',
    user: { ...previewLearner, displayName: 'Bạn học CNHSK', email: '', hskLevel: 1 },
  }
  return createElement(
    AuthContext.Provider,
    { value: auth },
    createElement(
      LearnerLayout,
      null,
      createElement(
        'div',
        {
          className:
            'mx-auto max-w-content px-4 pt-4 text-body-sm text-ink-secondary sm:px-6 lg:px-8',
        },
        'Bản xem trước dùng câu dịch mẫu, không gọi dịch vụ và không trừ lượt.',
      ),
      createElement(TranslationPreview, props),
    ),
  )
}

function DictionaryPreview({
  state,
  learner = false,
  rateLimited = false,
  initial = false,
  radical = false,
  screen = false,
}: {
  state: PreviewState
  learner?: boolean
  rateLimited?: boolean
  initial?: boolean
  radical?: boolean
  screen?: boolean
}) {
  const [draft, setDraft] = useState<DictionarySearch>({
    type: radical ? 'RADICAL' : 'HANZI',
    q: initial ? '' : '日',
    radical: '',
    strokes: radical ? '4' : '',
    page: 0,
  })
  const [submitted, setSubmitted] = useState(draft)
  const [searched, setSearched] = useState(!initial)
  const [formError, setFormError] = useState<string>()
  const [page, setPage] = useState(0)
  // Filtering is confined to this original, local preview dataset.
  const matches = screen
    ? libraryLexemes.filter((entry) => {
        if (submitted.type === 'RADICAL') {
          const detail = libraryLexemeDetails.find(({ id }) => id === entry.id)
          return (
            entry.kind === 'CHAR' &&
            (!submitted.radical || detail?.radical === submitted.radical) &&
            (!submitted.strokes || detail?.strokeCount === Number(submitted.strokes))
          )
        }
        const value =
          submitted.type === 'HANZI'
            ? entry.text
            : submitted.type === 'PINYIN'
              ? entry.pinyin
              : entry.meaningVi
        return value?.toLocaleLowerCase().includes(submitted.q.trim().toLocaleLowerCase())
      })
    : libraryLexemes
  const result =
    state === 'empty'
      ? libraryTranslationHint
      : {
          ...librarySearchResult,
          items: matches.slice(page * 20, (page + 1) * 20),
          total: matches.length,
        }
  const [selected, setSelected] = useState<number>()
  const selectedEntry = libraryLexemeDetails.find(({ id }) => id === selected)
  return createElement(TranslateWorkspaceView, {
    draft,
    onChange: (next) => {
      setDraft(next)
      setFormError(undefined)
    },
    onSubmit: () => {
      const error = validateDictionarySearch(draft)
      setFormError(error)
      if (error) return
      setSubmitted(draft)
      setSearched(true)
      setPage(0)
    },
    onSwap: () => setDraft({ ...draft, type: draft.type === 'MEANING' ? 'HANZI' : 'MEANING' }),
    output: createElement('p', { className: 'text-h3 text-ink-muted' }, 'Bản dịch'),
    details: selectedEntry
      ? createElement(LexemeEntryBody, { entry: selectedEntry, related: { onOpen: setSelected } })
      : null,
    listed: true,
    onPageChange: setPage,
    onRetry: () => undefined,
    onRadicalsRetry: () => undefined,
    onSelect: setSelected,
    selectedId: selected,
    state,
    result,
    formError,
    error: new ApiError({
      status: rateLimited ? 429 : 503,
      errorCode: rateLimited ? 'RATE_LIMITED' : 'NETWORK_ERROR',
      message: 'Chưa thể tra cứu. Vui lòng thử lại.',
      extra: { retryAfterSeconds: 60 },
    }),
    radicals: libraryRadicals,
    searched,
    page,
    loginTo: '/login?redirect=%2Ftranslate',
    registerTo: '/register?redirect=%2Ftranslate',
    guest: !learner,
    guestTrial: { limit: 10, remaining: rateLimited ? 0 : 7 },
  })
}

function DictionaryScreenPreview(props: Parameters<typeof DictionaryPreview>[0]) {
  const auth: AuthContextValue = {
    error: null,
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
    status: props.learner ? 'authenticated' : 'guest',
    user: props.learner
      ? { ...previewLearner, displayName: 'Bạn học CNHSK', email: '', hskLevel: 1 }
      : null,
  }
  return createElement(
    AuthContext.Provider,
    { value: auth },
    createElement(PublicLayout, null, createElement(DictionaryPreview, { ...props, screen: true })),
  )
}

function DetailPreview({
  state,
  entry,
  access,
}: {
  state: PreviewState
  entry: LexemeDetail
  access: LexemeSaveActionsProps['access']
}) {
  return createElement(LexemeDetailView, {
    entry,
    state: state === 'empty' ? 'error' : state,
    error: new ApiError({
      status: state === 'empty' ? 404 : 503,
      errorCode: state === 'empty' ? 'LEXEME_NOT_FOUND' : 'NETWORK_ERROR',
      message: state === 'empty' ? 'Mục từ không tồn tại.' : 'Chưa tải được mục từ.',
    }),
    backTo: '/translate?type=HANZI&q=日&page=0',
    onRetry: () => undefined,
    saveActions: createElement(LexemeSaveActions, {
      key: `${entry.id}:${access}`,
      entry,
      access,
      loginTo: `/login?redirect=%2Fdictionary%2F${entry.id}`,
      onReloadAuth: () => undefined,
      onCreateNote: async () => undefined,
      onLoadDecks: async () => libraryDeckSummaries,
      onAddCard: async () => undefined,
    }),
  })
}

function sampleDetail(id: number): LexemeDetail {
  const entry = libraryLexemeDetails.find((lexeme) => lexeme.id === id)
  if (!entry) throw new Error(`Missing original dictionary sample ${id}`)
  return entry
}

function DetailScreenPreview(props: Parameters<typeof DetailPreview>[0]) {
  const auth: AuthContextValue = {
    error: null,
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
    status: props.access === 'guest' ? 'guest' : 'authenticated',
    user:
      props.access === 'guest'
        ? null
        : {
            ...previewLearner,
            displayName: 'Bạn học CNHSK',
            email: '',
            hskLevel: 1,
            roles: props.access === 'learner' ? ['LEARNER'] : ['ADMIN'],
          },
  }
  return createElement(
    AuthContext.Provider,
    { value: auth },
    createElement(PublicLayout, null, createElement(DetailPreview, props)),
  )
}

const detailCharacterSample: LexemeDetail = {
  ...sampleDetail(101),
  sinoViet: 'nhật',
  examples: [{ cn: '今天有日光。', pinyin: 'Jīntiān yǒu rìguāng.', vi: 'Hôm nay có ánh nắng.' }],
  related: [{ id: 102, text: '月', pinyin: 'yuè', meaningVi: 'tháng; mặt trăng' }],
}

const detailWordSample: LexemeDetail = {
  ...sampleDetail(212),
  sinoViet: 'sinh nhật',
  examples: [
    {
      cn: '今天是我的生日。',
      pinyin: 'Jīntiān shì wǒ de shēngrì.',
      vi: 'Hôm nay là sinh nhật của tôi.',
    },
    { cn: '祝你生日快乐。', pinyin: 'Zhù nǐ shēngrì kuàilè.', vi: 'Chúc bạn sinh nhật vui vẻ.' },
  ],
}

const detailScreenEntries = (
  [
    ['scr-025-ready', 'Chi tiết từ — Redesign, học viên', detailCharacterSample, 'learner'],
    ['scr-025-guest', 'Chi tiết từ — Redesign, khách', detailCharacterSample, 'guest'],
    ['scr-025-word', 'Chi tiết từ — Từ ghép', detailWordSample, 'learner'],
    ['scr-025-grammar', 'Chi tiết từ — Ngữ pháp', sampleDetail(301), 'learner'],
    [
      'scr-025-readonly',
      'Chi tiết từ — Tài khoản không có quyền học viên',
      detailCharacterSample,
      'other',
    ],
    // Invalid media is local and intentional: exercise fallback copy without a remote request.
    [
      'scr-025-audio-error',
      'Chi tiết từ — Audio lỗi, bấm Nghe phát âm',
      { ...detailCharacterSample, audioUrl: 'data:audio/wav;base64,AA==' },
      'guest',
    ],
  ] as const
).map(([id, title, entry, access]) => ({
  fullFrame: true,
  group: 'screen' as const,
  id,
  scr: 'SCR-025',
  title,
  render: (state: PreviewState) => createElement(DetailScreenPreview, { state, entry, access }),
})) satisfies readonly PreviewEntry[]

function NotesPreview({ state, trash = false }: { state: PreviewState; trash?: boolean }) {
  const [filters, setFilters] = useState({ q: '', tag: '' })
  const [draft, setDraft] = useState<NoteDraft>()
  const [notice, setNotice] = useState('')
  return createElement(PersonalNotesView, {
    search: { q: '', tag: '', page: 0, tab: trash ? 'trash' : 'active' },
    filters,
    state,
    items: trash ? libraryTrash : libraryNotePage.items,
    total: trash ? libraryTrash.length : libraryNotePage.total,
    busy: false,
    opening: false,
    notice,
    error: new ApiError({
      status: 503,
      errorCode: 'UNAVAILABLE',
      message: 'Chưa tải được ghi chú. Vui lòng thử lại.',
    }),
    onFiltersChange: setFilters,
    onSearch: () => setNotice('Đây là dữ liệu mẫu của sổ tay.'),
    onClear: () => setFilters({ q: '', tag: '' }),
    onTabChange: () => setNotice('Xem mẫu Thùng rác riêng trong danh mục preview.'),
    onPageChange: () => undefined,
    onRetry: () => undefined,
    onActionRetry: () => undefined,
    onOpenRetry: () => undefined,
    onNew: () => setDraft(newNoteDraft()),
    onOpenNote: (id) => {
      const note = libraryNotes.find((item) => item.id === id)
      if (note) setDraft(noteDraft(note))
    },
    onDelete: () => setNotice('Xoá ghi chú cần xác nhận trong trang thật.'),
    onRestore: () => setNotice('Khôi phục mẫu — không gọi backend.'),
    editor: draft
      ? createElement(NoteEditor, {
          draft,
          pending: false,
          opening: false,
          onChange: setDraft,
          onSubmit: () => setNotice('Đã nhận bản nháp mẫu — không gọi backend.'),
          onOpenNote: () => undefined,
        })
      : undefined,
  })
}

function DeckListPreview({ state }: { state: PreviewState }) {
  const [draft, setDraft] = useState(emptyDeckDraft)
  const [page, setPage] = useState(0)
  return createElement(DeckListView, {
    state,
    decks: libraryDeckSummaries,
    draft,
    page,
    pending: false,
    topics: [{ id: 11, name: 'Chủ đề tự soạn', totalWords: 2 }],
    topicsState: 'ready',
    error: new ApiError({
      status: 503,
      errorCode: 'UNAVAILABLE',
      message: 'Chưa tải được bộ thẻ.',
    }),
    onPageChange: setPage,
    onChange: setDraft,
    onCreate: () => undefined,
    onRetry: () => undefined,
    onTopicsRetry: () => undefined,
  })
}

function DeckDetailPreview({
  state,
  reviewing = false,
}: {
  state: PreviewState
  reviewing?: boolean
}) {
  const deck = libraryDecks[0]!
  const [deckDraft, setDeckDraft] = useState({
    ...emptyDeckDraft(),
    name: deck.name,
    description: deck.description ?? '',
  })
  const [cardDraft, setCardDraft] = useState<CardDraft>(emptyCardDraft)
  const [page, setPage] = useState(0)
  const [review, setReview] = useState(reviewing)
  const [flipped, setFlipped] = useState(false)
  const [index, setIndex] = useState(0)
  const [ahead, setAhead] = useState(false)
  return createElement(DeckDetailView, {
    state: state === 'empty' ? 'ready' : state,
    deck: {
      ...deck,
      cards: state === 'empty' ? [] : deck.cards,
      cardCount: state === 'empty' ? 0 : deck.cardCount,
      dueCount: state === 'empty' ? 0 : deck.dueCount,
    },
    error: new ApiError({
      status: 503,
      errorCode: 'UNAVAILABLE',
      message: 'Chưa tải được bộ thẻ.',
    }),
    deckDraft,
    cardDraft,
    page,
    busy: false,
    onPageChange: setPage,
    onRetry: () => undefined,
    onDeckChange: setDeckDraft,
    onCardChange: setCardDraft,
    onSaveDeck: () => undefined,
    onSaveCard: () => undefined,
    onNewCard: () => setCardDraft(emptyCardDraft()),
    onEditCard: (card) => setCardDraft({ id: card.id, front: card.front, back: card.back }),
    onDeleteCard: () => undefined,
    onDeleteDeck: () => undefined,
    onReview: () => {
      setReview(true)
      setIndex(0)
      setFlipped(false)
    },
    review: review
      ? createElement(FlashcardReviewView, {
          state,
          card: deck.cards[index],
          remaining: deck.cards.length - index,
          flipped,
          pending: false,
          completed: state === 'ready' && index === deck.cards.length,
          ahead,
          error: new ApiError({
            status: 503,
            errorCode: 'UNAVAILABLE',
            message: 'Chưa tải được thẻ ôn.',
          }),
          onFlip: () => setFlipped(true),
          onRate: () => {
            setIndex((current) => Math.min(current + 1, deck.cards.length))
            setFlipped(false)
          },
          onRetry: () => undefined,
          onAhead: () => setAhead(true),
          onClose: () => setReview(false),
        })
      : undefined,
  })
}

export const previews = [
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-029-ready',
    scr: 'SCR-029',
    title: 'Chi tiết bộ thẻ — Chủ sở hữu',
    render: (state) => createElement(DeckDetailScreenPreview, { state }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-029-public',
    scr: 'SCR-029',
    title: 'Chi tiết bộ thẻ — Preview khách chỉ đọc',
    render: (state) => createElement(DeckDetailScreenPreview, { state, guest: true }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-029-editing',
    scr: 'SCR-029',
    title: 'Chi tiết bộ thẻ — Sửa thẻ',
    states: ['ready'],
    render: (state) => createElement(DeckDetailScreenPreview, { state, editing: true }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-029-review',
    scr: 'SCR-029',
    title: 'Chi tiết bộ thẻ — Ôn thẻ',
    states: ['ready'],
    render: (state) => createElement(DeckDetailScreenPreview, { state, review: true }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-029-flipped',
    scr: 'SCR-029',
    title: 'Chi tiết bộ thẻ — Đã lật',
    states: ['ready'],
    render: (state) =>
      createElement(DeckDetailScreenPreview, { state, review: true, flipped: true }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-028-ready',
    scr: 'SCR-028',
    title: 'Bộ thẻ của tôi — Redesign',
    render: (state) => createElement(DeckListScreenPreview, { state }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-028-topic',
    scr: 'SCR-028',
    title: 'Bộ thẻ của tôi — Tạo từ chủ đề',
    states: ['ready'],
    render: (state) => createElement(DeckListScreenPreview, { state, source: 'topic' }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-028-topic-error',
    scr: 'SCR-028',
    title: 'Bộ thẻ của tôi — Lỗi tải chủ đề',
    states: ['ready'],
    render: (state) =>
      createElement(DeckListScreenPreview, { state, source: 'topic', topicState: 'error' }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-028-pending',
    scr: 'SCR-028',
    title: 'Bộ thẻ của tôi — Đang tạo',
    states: ['ready'],
    render: (state) => createElement(DeckListScreenPreview, { state, pending: true }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-028-limit',
    scr: 'SCR-028',
    title: 'Bộ thẻ của tôi — Bấm Tạo để xem lỗi giới hạn',
    states: ['ready'],
    render: (state) => createElement(DeckListScreenPreview, { state, limited: true }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-027-ready',
    scr: 'SCR-027',
    title: 'Sổ tay cá nhân — Redesign',
    render: (state) => createElement(PersonalNotesScreenPreview, { state }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-027-editing',
    scr: 'SCR-027',
    title: 'Sổ tay cá nhân — Mở ghi chú mẫu',
    states: ['ready'],
    render: (state) => createElement(PersonalNotesScreenPreview, { state, editing: true }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-027-new',
    scr: 'SCR-027',
    title: 'Sổ tay cá nhân — Soạn ghi chú',
    states: ['ready'],
    render: (state) => createElement(PersonalNotesScreenPreview, { state, creating: true }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-027-trash',
    scr: 'SCR-027',
    title: 'Sổ tay cá nhân — Thùng rác',
    render: (state) => createElement(PersonalNotesScreenPreview, { state, trash: true }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-026-ready',
    scr: 'SCR-026',
    title: 'Dịch — Câu, Redesign',
    render: (state) => createElement(TranslationScreenPreview, { state }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-026-zh-vi',
    scr: 'SCR-026',
    title: 'Dịch — Câu, Trung sang Việt',
    render: (state) => createElement(TranslationScreenPreview, { state, from: 'ZH' }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-026-cached',
    scr: 'SCR-026',
    title: 'Dịch — Câu, Bản dịch từ bộ nhớ đệm',
    states: ['ready'],
    render: (state) => createElement(TranslationScreenPreview, { state, cached: true }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-026-insufficient',
    scr: 'SCR-026',
    title: 'Dịch — Câu, Hết lượt và điểm',
    states: ['error'],
    render: (state) => createElement(TranslationScreenPreview, { state, insufficient: true }),
  },
  ...detailScreenEntries,
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-024-ready',
    scr: 'SCR-024',
    title: 'Dịch — Tra từ, Redesign, khách',
    render: (state) => createElement(DictionaryScreenPreview, { state }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-024-initial',
    scr: 'SCR-024',
    title: 'Dịch — Tra từ, Chưa tìm kiếm',
    states: ['ready'],
    render: (state) => createElement(DictionaryScreenPreview, { state, initial: true }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-024-learner',
    scr: 'SCR-024',
    title: 'Dịch — Tra từ, Redesign, học viên',
    render: (state) => createElement(DictionaryScreenPreview, { state, learner: true }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-024-radical',
    scr: 'SCR-024',
    title: 'Dịch — Tra từ, Bộ thủ và số nét',
    render: (state) => createElement(DictionaryScreenPreview, { state, radical: true }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-024-rate-limited',
    scr: 'SCR-024',
    title: 'Dịch — Tra từ, Khách chạm hạn mức',
    states: ['error'],
    render: (state) => createElement(DictionaryScreenPreview, { state, rateLimited: true }),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'scr-024-learner-rate-limited',
    scr: 'SCR-024',
    title: 'Dịch — Tra từ, Học viên chạm hạn mức',
    states: ['error'],
    render: (state) =>
      createElement(DictionaryScreenPreview, { state, rateLimited: true, learner: true }),
  },
  {
    group: 'screen',
    id: 'library-dictionary-samples',
    scr: 'SCR-024',
    title: 'Dịch — Tra từ, Khách',
    render: (state) => createElement(DictionaryPreview, { state }),
  },
  {
    group: 'screen',
    id: 'library-detail-samples',
    scr: 'SCR-025',
    title: 'Chi tiết chữ Hán — Khách',
    render: (state) =>
      createElement(DetailPreview, { state, entry: sampleDetail(101), access: 'guest' }),
  },
  {
    group: 'screen',
    id: 'library-dictionary-learner',
    scr: 'SCR-024',
    title: 'Dịch — Tra từ, Học viên',
    render: (state) => createElement(DictionaryPreview, { state, learner: true }),
  },
  {
    group: 'screen',
    id: 'library-dictionary-rate-limited',
    scr: 'SCR-024',
    title: 'Dịch — Tra từ, Khách hết lượt tra',
    states: ['error'],
    render: (state) => createElement(DictionaryPreview, { state, rateLimited: true }),
  },
  {
    group: 'screen',
    id: 'library-detail-learner',
    scr: 'SCR-025',
    title: 'Chi tiết từ vựng — Học viên',
    render: (state) =>
      createElement(DetailPreview, { state, entry: sampleDetail(212), access: 'learner' }),
  },
  {
    group: 'screen',
    id: 'library-detail-grammar',
    scr: 'SCR-025',
    title: 'Chi tiết ngữ pháp — Tài khoản không có LEARNER',
    render: (state) =>
      createElement(DetailPreview, { state, entry: sampleDetail(301), access: 'other' }),
  },
  {
    group: 'screen',
    id: 'library-translation-samples',
    scr: 'SCR-026',
    title: 'Dịch — Câu, Dữ liệu mẫu',
    states: ['ready', 'loading', 'empty', 'error'],
    render: (state) => createElement(TranslationPreview, { state }),
  },
  {
    group: 'screen',
    id: 'library-note-samples',
    scr: 'SCR-027',
    title: 'Sổ tay — Dữ liệu mẫu',
    render: (state) => createElement(NotesPreview, { state }),
  },
  {
    group: 'screen',
    id: 'library-note-trash',
    scr: 'SCR-027',
    title: 'Sổ tay — Thùng rác',
    render: (state) => createElement(NotesPreview, { state, trash: true }),
  },
  {
    group: 'screen',
    id: 'library-deck-samples',
    scr: 'SCR-028',
    title: 'Bộ thẻ — Dữ liệu mẫu',
    render: (state) => createElement(DeckListPreview, { state }),
  },
  {
    group: 'screen',
    id: 'library-card-samples',
    scr: 'SCR-029',
    title: 'Thẻ — Dữ liệu mẫu',
    render: (state) => createElement(DeckDetailPreview, { state }),
  },
  {
    group: 'screen',
    id: 'library-review-samples',
    scr: 'SCR-029',
    title: 'Ôn bộ thẻ — Dữ liệu mẫu',
    render: (state) => createElement(DeckDetailPreview, { state, reviewing: true }),
  },
] satisfies readonly PreviewEntry[]
