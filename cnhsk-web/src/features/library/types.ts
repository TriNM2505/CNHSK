export type LexemeKind = 'CHAR' | 'WORD' | 'GRAMMAR'
export type DictionarySearchType = 'HANZI' | 'PINYIN' | 'MEANING'
export type TranslationLanguage = 'ZH' | 'VI'
export type NoteSourceType = 'DICTIONARY' | 'TRANSLATION' | 'MANUAL'
export type ReviewRating = 'AGAIN' | 'HARD' | 'GOOD' | 'EASY'

export type PageParams = {
  page?: number
  size?: number
}

export type DictionarySearchParams = PageParams & {
  type: DictionarySearchType
  q: string
}

export type RadicalSearchParams = PageParams & {
  radical?: string
  strokes?: number
}

export type LexemeSummary = {
  id: number
  kind: LexemeKind
  text: string
  pinyin?: string | null
  sinoViet?: string | null
  meaningVi: string
  hskLevel?: number | null
  approximate: boolean
}

export type SearchResult = {
  items: LexemeSummary[]
  total: number
  hint?: 'USE_TRANSLATE' | null
}

export type RadicalCount = {
  radical: string
  charCount: number
}

export type LexemeRef = {
  id: number
  text: string
  pinyin?: string | null
  meaningVi: string
}

export type LexemeExample = {
  cn?: string
  pinyin?: string | null
  vi?: string
}

export type LexemeDetail = Omit<LexemeSummary, 'approximate'> & {
  audioUrl?: string | null
  radical?: string | null
  strokeCount?: number | null
  strokeData?: Record<string, unknown> | null
  structure?: string | null
  examples: LexemeExample[]
  components: LexemeRef[]
  containedIn: LexemeRef[]
  related: LexemeRef[]
}

export type TranslateRequest = {
  text: string
  from: TranslationLanguage
  to: TranslationLanguage
}

export type Segment = {
  text: string
  pinyin?: string | null
  lexemeId?: number | null
}

export type TranslateResult = {
  translated: string
  pinyin?: string | null
  segments: Segment[]
  cached: boolean
  consumption: 'QUOTA' | 'CREDIT' | null
}

export type NoteRequest = {
  title?: string | null
  content: string
  tags?: string[]
  lexemeId?: number | null
  sourceType?: NoteSourceType | null
}

export type Note = {
  id: number
  title?: string | null
  content: string
  tags: string[]
  lexemeId?: number | null
  sourceType?: string | null
  createdAt: string
  updatedAt: string
}

export type NoteListItem = Omit<Note, 'content' | 'createdAt'> & {
  snippet: string
}

export type NotePage = {
  items: NoteListItem[]
  total: number
}

export type NoteListParams = PageParams & {
  q?: string
  tag?: string
}

export type TrashNote = NoteListItem & {
  deletedAt: string
  restorableUntil: string
}

export type DeckRequest = {
  name: string
  description?: string | null
  topicId?: number | null
}

export type DeckSummary = {
  id: number
  name: string
  description?: string | null
  cardCount: number
  dueCount: number
  createdAt: string
}

export type Card = {
  id: number
  lexemeId?: number | null
  front: string
  back: string
  dueAt?: string | null
  reviewCount: number
  lapseCount: number
}

export type DeckDetail = DeckSummary & {
  cards: Card[]
}

export type CardRequest = {
  lexemeId?: number | null
  front?: string | null
  back?: string | null
}

export type ReviewRequest = {
  rating: ReviewRating
}

export type ReviewResult = {
  cardId: number
  nextReviewAt: string
  intervalDays: number
}
