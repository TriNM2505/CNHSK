import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import type { Card, DeckDetail, Note, TranslateResult } from '../types'
import * as dictionary from './dictionary'
import * as flashcards from './flashcards'
import * as notes from './notes'
import { translate } from './translation'

const origin = 'http://api.test'
const note: Note = {
  id: 1,
  content: 'Ghi chú tự soạn.',
  tags: [],
  createdAt: '2026-11-01T00:00:00Z',
  updatedAt: '2026-11-01T00:00:00Z',
}
const card: Card = { id: 2, front: '今天', back: 'Hôm nay', reviewCount: 0, lapseCount: 0 }
const deck: DeckDetail = {
  id: 3,
  name: 'Bộ tự soạn',
  cardCount: 1,
  dueCount: 1,
  createdAt: '2026-11-01T00:00:00Z',
  cards: [card],
}
const translated: TranslateResult = {
  translated: '今天我看书。',
  pinyin: null,
  segments: [],
  cached: true,
  consumption: null,
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function clearCsrf() {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
}

beforeEach(() => {
  vi.stubEnv('VITE_API_BASE_URL', origin)
  clearCsrf()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  clearCsrf()
})

describe('library API with the real shared client (§13; AT-01 transport only)', () => {
  it('FR001_public dictionary GETs preserve Unicode queries, pagination, nullable data and AbortSignal', async () => {
    const result = {
      items: [
        { id: 9, kind: 'CHAR', text: '日', meaningVi: 'ngày', hskLevel: 9, approximate: false },
      ],
      total: 1,
    }
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json(result))
      .mockResolvedValueOnce(json([{ radical: '口', charCount: 2 }]))
      .mockResolvedValueOnce(json(result))
      .mockResolvedValueOnce(
        json({
          ...result.items[0],
          examples: [],
          components: [],
          containedIn: [],
          related: [],
          strokeData: null,
        }),
      )
    vi.stubGlobal('fetch', fetchMock)
    const signal = new AbortController().signal

    await expect(
      dictionary.searchDictionary({ type: 'PINYIN', q: 'nǐ hǎo &', page: 1, size: 50 }, signal),
    ).resolves.toEqual(result)
    await dictionary.listRadicals(signal)
    await dictionary.searchByRadical({ radical: '口', strokes: 6 }, signal)
    await dictionary.getLexeme(9, signal)

    const searchUrl = new URL(String(fetchMock.mock.calls[0]![0]))
    expect(searchUrl.pathname).toBe('/api/public/dictionary/search')
    expect(Object.fromEntries(searchUrl.searchParams)).toEqual({
      type: 'PINYIN',
      q: 'nǐ hǎo &',
      page: '1',
      size: '50',
    })
    expect(fetchMock.mock.calls.slice(1).map(([url]) => String(url))).toEqual([
      `${origin}/api/public/dictionary/radicals`,
      `${origin}/api/public/dictionary/by-radical?radical=%E5%8F%A3&strokes=6&page=0&size=20`,
      `${origin}/api/public/dictionary/lexemes/9`,
    ])
    for (const [, init] of fetchMock.mock.calls) {
      expect(init).toMatchObject({ method: 'GET', credentials: 'include', signal })
      const headers = new Headers(init?.headers)
      expect(headers.get('X-Client-Type')).toBe('WEB')
      expect(headers.has('X-XSRF-TOKEN')).toBe(false)
      expect(headers.has('Authorization')).toBe(false)
    }
  })

  it('preserves optional filters and uses contract defaults without an HSK UI restriction', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockImplementation(async () => json({ items: [], total: 0 }))
    vi.stubGlobal('fetch', fetchMock)
    await dictionary.searchDictionary({ type: 'HANZI', q: '日' })
    await dictionary.searchByRadical({ strokes: 4 })
    await notes.listNotes()
    await notes.listNotes({ q: 'gia đình & bạn', tag: 'hsk1', page: 2, size: 10 })
    const urls = fetchMock.mock.calls.map(([url]) => new URL(String(url)))
    expect(Object.fromEntries(urls[0]!.searchParams)).toEqual({
      type: 'HANZI',
      q: '日',
      page: '0',
      size: '20',
    })
    expect(Object.fromEntries(urls[1]!.searchParams)).toEqual({
      strokes: '4',
      page: '0',
      size: '20',
    })
    expect(Object.fromEntries(urls[2]!.searchParams)).toEqual({ page: '0', size: '20' })
    expect(Object.fromEntries(urls[3]!.searchParams)).toEqual({
      q: 'gia đình & bạn',
      tag: 'hsk1',
      page: '2',
      size: '10',
    })
  })

  it('FR001_reads private resources at their contract paths using the current cookie session', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json(note))
      .mockResolvedValueOnce(json([]))
      .mockResolvedValueOnce(json([deck]))
      .mockResolvedValueOnce(json(deck))
      .mockResolvedValueOnce(json([card]))
      .mockResolvedValueOnce(json([card]))
    vi.stubGlobal('fetch', fetchMock)
    const signal = new AbortController().signal
    await expect(notes.getNote(1, signal)).resolves.toEqual(note)
    await expect(notes.listTrash(signal)).resolves.toEqual([])
    await flashcards.listDecks(signal)
    await expect(flashcards.getDeck(3, signal)).resolves.toEqual(deck)
    await flashcards.listDueCards(3, undefined, signal)
    await flashcards.listDueCards(3, true, signal)
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      `${origin}/api/notes/1`,
      `${origin}/api/notes/trash`,
      `${origin}/api/flashcard-decks`,
      `${origin}/api/flashcard-decks/3`,
      `${origin}/api/flashcard-decks/3/due?ahead=false`,
      `${origin}/api/flashcard-decks/3/due?ahead=true`,
    ])
    for (const [, init] of fetchMock.mock.calls) {
      expect(init).toMatchObject({ method: 'GET', credentials: 'include', signal })
    }
  })

  const mutations = [
    {
      path: '/api/notes',
      method: 'POST',
      body: { content: 'Tự soạn', sourceType: 'MANUAL' },
      invoke: () => notes.createNote({ content: 'Tự soạn', sourceType: 'MANUAL' }),
      response: note,
      status: 201,
    },
    {
      path: '/api/notes/1',
      method: 'PUT',
      body: { title: null, content: 'Đã sửa', tags: [] },
      invoke: () => notes.updateNote(1, { title: null, content: 'Đã sửa', tags: [] }),
      response: note,
    },
    { path: '/api/notes/1', method: 'DELETE', invoke: () => notes.deleteNote(1) },
    {
      path: '/api/notes/1/restore',
      method: 'POST',
      invoke: () => notes.restoreNote(1),
      response: note,
    },
    {
      path: '/api/flashcard-decks',
      method: 'POST',
      body: { name: 'Bộ tự soạn', topicId: 7 },
      invoke: () => flashcards.createDeck({ name: 'Bộ tự soạn', topicId: 7 }),
      response: deck,
      status: 201,
    },
    {
      path: '/api/flashcard-decks/3',
      method: 'PATCH',
      body: { name: 'Tên mới', description: null },
      invoke: () => flashcards.updateDeck(3, { name: 'Tên mới', description: null }),
      response: deck,
    },
    { path: '/api/flashcard-decks/3', method: 'DELETE', invoke: () => flashcards.deleteDeck(3) },
    {
      path: '/api/flashcard-decks/3/cards',
      method: 'POST',
      body: { lexemeId: 201 },
      invoke: () => flashcards.addCard(3, { lexemeId: 201 }),
      response: card,
      status: 201,
    },
    {
      path: '/api/flashcard-decks/3/cards',
      method: 'POST',
      body: { front: '早上', back: 'Buổi sáng' },
      invoke: () => flashcards.addCard(3, { front: '早上', back: 'Buổi sáng' }),
      response: card,
      status: 201,
    },
    {
      path: '/api/flashcard-decks/3/cards/2',
      method: 'PATCH',
      body: { front: '明天', back: 'Ngày mai' },
      invoke: () => flashcards.updateCard(3, 2, { front: '明天', back: 'Ngày mai' }),
      response: card,
    },
    {
      path: '/api/flashcard-decks/3/cards/2',
      method: 'DELETE',
      invoke: () => flashcards.deleteCard(3, 2),
    },
    {
      path: '/api/flashcards/2/review',
      method: 'POST',
      body: { rating: 'GOOD' },
      invoke: () => flashcards.reviewCard(2, { rating: 'GOOD' }),
      response: { cardId: 2, intervalDays: 3, nextReviewAt: '2026-11-06T00:00:00Z' },
    },
    {
      path: '/api/translate',
      method: 'POST',
      body: { text: 'Hôm nay tôi đọc sách.', from: 'VI', to: 'ZH' },
      invoke: () => translate({ text: 'Hôm nay tôi đọc sách.', from: 'VI', to: 'ZH' }),
      response: translated,
    },
  ]

  it.each(mutations)(
    'FR001_$method $path uses shared CSRF and preserves the request and response',
    async ({ path, method, body, invoke, response, status }) => {
      document.cookie = 'XSRF-TOKEN=library%20token; path=/'
      const fetchMock = vi
        .fn<typeof fetch>()
        .mockResolvedValue(
          response === undefined
            ? new Response(null, { status: 204 })
            : json(response, status ?? 200),
        )
      vi.stubGlobal('fetch', fetchMock)
      await expect(invoke()).resolves.toEqual(response)
      expect(fetchMock).toHaveBeenCalledOnce()
      const [url, init] = fetchMock.mock.calls[0]!
      expect(url).toBe(`${origin}${path}`)
      expect(init).toMatchObject({ method, credentials: 'include' })
      const headers = new Headers(init?.headers)
      expect(headers.get('X-XSRF-TOKEN')).toBe('library token')
      expect(headers.get('X-Client-Type')).toBe('WEB')
      expect(init?.body).toBe(body === undefined ? undefined : JSON.stringify(body))
      if (body) {
        expect(headers.get('Content-Type')).toBe('application/json')
        expect(JSON.parse(String(init?.body))).not.toHaveProperty('userId')
      }
    },
  )

  it.each([
    { status: 401, errorCode: 'UNAUTHENTICATED', invoke: () => notes.getNote(1) },
    { status: 403, errorCode: 'COLLECTION_NOT_OWNED', invoke: () => flashcards.getDeck(3) },
    { status: 404, errorCode: 'LEXEME_NOT_FOUND', invoke: () => dictionary.getLexeme(999) },
    {
      status: 409,
      errorCode: 'CARD_DUPLICATE',
      invoke: () => flashcards.addCard(3, { lexemeId: 201 }),
    },
    {
      status: 422,
      errorCode: 'VALIDATION_FAILED',
      invoke: () => dictionary.searchDictionary({ type: 'HANZI', q: '' }),
    },
    {
      status: 429,
      errorCode: 'RATE_LIMITED',
      invoke: () => dictionary.searchDictionary({ type: 'HANZI', q: '日' }),
    },
    {
      status: 402,
      errorCode: 'INSUFFICIENT_CREDITS',
      invoke: () => translate({ text: '你好', from: 'ZH', to: 'VI' }),
    },
    {
      status: 503,
      errorCode: 'TRANSLATE_UNAVAILABLE',
      invoke: () => translate({ text: '你好', from: 'ZH', to: 'VI' }),
    },
  ])(
    'preserves $status $errorCode for the future screen to handle',
    async ({ status, errorCode, invoke }) => {
      document.cookie = 'XSRF-TOKEN=library-token; path=/'
      const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
        json(
          {
            error_code: errorCode,
            message: 'Thông báo an toàn',
            request_id: 'req-library',
            retryAfterSeconds: 60,
            loginSuggested: true,
            details: { kind: 'CARDS', limit: 500 },
          },
          status,
        ),
      )
      vi.stubGlobal('fetch', fetchMock)
      const result = invoke()
      await expect(result).rejects.toBeInstanceOf(ApiError)
      await expect(result).rejects.toMatchObject({
        status,
        errorCode,
        requestId: 'req-library',
        extra: { retryAfterSeconds: 60, loginSuggested: true },
        details: { kind: 'CARDS', limit: 500 },
      })
      expect(fetchMock).toHaveBeenCalledOnce()
    },
  )

  it('propagates cancellation of a dictionary request', async () => {
    const aborted = new DOMException('Aborted', 'AbortError')
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockRejectedValue(aborted))
    const controller = new AbortController()
    controller.abort()
    await expect(dictionary.listRadicals(controller.signal)).rejects.toBe(aborted)
  })
})
