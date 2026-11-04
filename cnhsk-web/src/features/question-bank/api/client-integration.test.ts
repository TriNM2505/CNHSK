import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import { toQuestionUpdate } from '../questionEditForm'
import type { QuestionDetail, QuestionUpdate } from '../types'
import { getKnowledgeNode, getKnowledgeTree } from './knowledge'
import { archiveQuestion, getQuestion, listQuestions, updateQuestion } from './questions'

const origin = 'http://api.test'
const detail: QuestionDetail = {
  id: 901,
  code: null,
  qtype: 'FILL_BLANK',
  hskLevel: 9,
  difficulty: 2,
  stem: 'Điền từ nước: 我喝___。',
  options: [],
  correctKey: '水',
  explanation: null,
  audioUrl: null,
  imageUrl: null,
  source: 'AI',
  status: 'PENDING_REVIEW',
  reviewReason: null,
  labels: [{ kid: 'TV1-901', nameVi: 'Nút tự soạn', role: 'PRIMARY' }],
  used: true,
}
const update: QuestionUpdate = {
  stem: detail.stem,
  options: [],
  correctKey: '水',
  explanation: '水 nghĩa là nước.',
  hskLevel: 3,
  difficulty: 2,
  audioUrl: null,
  imageUrl: null,
  primaryKid: 'TV1-901',
  secondaryKids: [],
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

describe('question-bank API foundation with the real shared client (§13)', () => {
  it('T13_AT26_AT27_sends a Unicode list query and normalized PUT body to the fake server, then archives without a body', async () => {
    document.cookie = 'XSRF-TOKEN=test-question-csrf; path=/'
    const query = { hskLevel: 3, kid: 'NP3-901', q: '𠀀😀 & 今天?', page: 1, size: 10 }
    const body = toQuestionUpdate({
      ...update,
      primaryKid: ' tv1-901 ',
      secondaryKids: [' np3-901 '],
      correctKey: ' 𠀀😀 ',
      audioUrl: '',
      imageUrl: null,
    })
    const server = vi.fn<typeof fetch>().mockImplementation(async (input, init) => {
      const url = new URL(String(input))
      expect(url.origin).toBe(origin)
      expect(init?.credentials).toBe('include')
      if (init?.method === 'GET' && url.pathname === '/api/admin/questions') {
        expect(Object.fromEntries(url.searchParams)).toEqual({
          hskLevel: '3',
          kid: 'NP3-901',
          q: '𠀀😀 & 今天?',
          page: '1',
          size: '10',
        })
        return json({ items: [], page: 1, size: 10, totalElements: 0 })
      }
      expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('test-question-csrf')
      if (init?.method === 'PUT' && url.pathname === '/api/admin/questions/901') {
        expect(JSON.parse(String(init.body))).toEqual({
          ...update,
          primaryKid: 'TV1-901',
          secondaryKids: ['NP3-901'],
          correctKey: '𠀀😀',
          audioUrl: null,
          imageUrl: null,
        })
        return json({ ...detail, ...body })
      }
      expect(url.pathname).toBe('/api/admin/questions/901/archive')
      expect(init?.method).toBe('PATCH')
      expect(init?.body).toBeUndefined()
      return json({ ...detail, status: 'ARCHIVED' })
    })
    vi.stubGlobal('fetch', server)
    await expect(listQuestions(query)).resolves.toMatchObject({ page: 1, size: 10 })
    await expect(updateQuestion(901, body)).resolves.toMatchObject({ correctKey: '𠀀😀' })
    await expect(archiveQuestion(901)).resolves.toMatchObject({ status: 'ARCHIVED' })
    expect(server).toHaveBeenCalledTimes(3)
  })

  it.each([
    [404, 'QUESTION_NOT_FOUND'],
    [409, 'GRADING_CHANGE_NOT_ALLOWED'],
    [409, 'QUESTION_ARCHIVED'],
    [422, 'MALFORMED_QUESTION'],
    [422, 'KNOWLEDGE_NODE_REQUIRED'],
  ] as const)(
    'T13_AT27_preserves PUT %s %s and field details through the real client',
    async (status, errorCode) => {
      document.cookie = 'XSRF-TOKEN=test-question-csrf; path=/'
      const details = [
        { field: 'correctKey', message: 'Đáp án cần kiểm tra.' },
        { field: 'unknownField', message: 'Lỗi bổ sung từ máy chủ.' },
      ]
      vi.stubGlobal(
        'fetch',
        vi.fn<typeof fetch>().mockImplementation(async (input, init) => {
          expect(String(input)).toBe(`${origin}/api/admin/questions/901`)
          expect(init?.method).toBe('PUT')
          expect(JSON.parse(String(init?.body))).toEqual(update)
          return json(
            {
              error_code: errorCode,
              message: 'Lỗi kiểm thử an toàn.',
              request_id: 'req-mutation',
              details,
            },
            status,
          )
        }),
      )
      await expect(updateQuestion(901, update)).rejects.toMatchObject({
        status,
        errorCode,
        requestId: 'req-mutation',
        details,
      })
    },
  )

  it('T13_AT27_preserves archive 404 through the real client', async () => {
    document.cookie = 'XSRF-TOKEN=test-question-csrf; path=/'
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockImplementation(async (input, init) => {
        expect(String(input)).toBe(`${origin}/api/admin/questions/901/archive`)
        expect(init?.method).toBe('PATCH')
        expect(init?.body).toBeUndefined()
        return json(
          {
            error_code: 'QUESTION_NOT_FOUND',
            message: 'Không tìm thấy câu hỏi.',
            request_id: 'req-archive',
          },
          404,
        )
      }),
    )
    await expect(archiveQuestion(901)).rejects.toMatchObject({
      status: 404,
      errorCode: 'QUESTION_NOT_FOUND',
      requestId: 'req-archive',
    })
  })

  it('FR001_reads the tree and node detail with the cookie session and AbortSignal', async () => {
    const node = {
      kid: 'NP1-901',
      domain: 'NP',
      hskLevel: 1,
      nameVi: 'Nút mẫu',
      nameZh: '示例',
      canDo: null,
      skillType: null,
      prereqKids: [],
      supportKids: [],
    }
    const nodeDetail = {
      ...node,
      dependentKids: ['NP2-901'],
      topics: [],
      lexemeCount: 0,
      approvedQuestionCount: 2,
    }
    const tree = { nodes: [node] }
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json(tree))
      .mockResolvedValueOnce(json(nodeDetail))
    vi.stubGlobal('fetch', fetchMock)
    const signal = new AbortController().signal

    await expect(getKnowledgeTree({ domain: 'NP', hskLevel: 7 }, signal)).resolves.toEqual(tree)
    await expect(getKnowledgeNode('NP1-901', signal)).resolves.toEqual(nodeDetail)

    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      `${origin}/api/knowledge-tree?domain=NP&hskLevel=7`,
      `${origin}/api/knowledge-tree/nodes/NP1-901`,
    ])
    for (const [, init] of fetchMock.mock.calls) {
      expect(init).toMatchObject({ method: 'GET', credentials: 'include', signal })
      const headers = new Headers(init?.headers)
      expect(headers.get('X-Client-Type')).toBe('WEB')
      expect(headers.has('X-XSRF-TOKEN')).toBe(false)
      expect(headers.has('Authorization')).toBe(false)
    }
  })

  it('preserves Unicode filters, page zero and the full question API HSK range', async () => {
    const page = { items: [], page: 0, size: 100, totalElements: 0 }
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(json(page))
    vi.stubGlobal('fetch', fetchMock)
    await expect(
      listQuestions({
        hskLevel: 9,
        qtype: 'ESSAY',
        status: 'ARCHIVED',
        kid: 'NP3-901',
        q: '今天 & 明天?',
        page: 0,
        size: 100,
      }),
    ).resolves.toEqual(page)
    const url = new URL(String(fetchMock.mock.calls[0]![0]))
    expect(url.pathname).toBe('/api/admin/questions')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      hskLevel: '9',
      qtype: 'ESSAY',
      status: 'ARCHIVED',
      kid: 'NP3-901',
      q: '今天 & 明天?',
      page: '0',
      size: '100',
    })
  })

  it('omits unspecified filters and uses page 0 and size 10 from the contract', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(async () => json({ nodes: [] }))
    vi.stubGlobal('fetch', fetchMock)
    await getKnowledgeTree()
    await listQuestions()
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      `${origin}/api/knowledge-tree`,
      `${origin}/api/admin/questions?page=0&size=10`,
    ])
  })

  it('leaves supplied invalid values for the server to validate and encodes KID path segments', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(async () => json({}))
    vi.stubGlobal('fetch', fetchMock)
    await getKnowledgeTree({ hskLevel: 0 })
    await listQuestions({ hskLevel: 0, page: 0, size: 0, q: '' })
    await getKnowledgeNode('NP1-901/?#')
    const urls = fetchMock.mock.calls.map(([url]) => new URL(String(url)))
    expect(urls[0]!.searchParams.get('hskLevel')).toBe('0')
    expect(Object.fromEntries(urls[1]!.searchParams)).toEqual({
      hskLevel: '0',
      q: '',
      page: '0',
      size: '0',
    })
    expect(urls[2]!.pathname).toBe('/api/knowledge-tree/nodes/NP1-901%2F%3F%23')
    expect(urls[2]!.search).toBe('')
    expect(urls[2]!.hash).toBe('')
  })

  it('FR001_reads a question without dropping nullable fields, labels or used', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(json(detail))
    vi.stubGlobal('fetch', fetchMock)
    const signal = new AbortController().signal
    await expect(getQuestion(901, signal)).resolves.toEqual(detail)
    expect(fetchMock.mock.calls[0]![0]).toBe(`${origin}/api/admin/questions/901`)
    expect(fetchMock.mock.calls[0]![1]).toMatchObject({
      method: 'GET',
      credentials: 'include',
      signal,
    })
  })

  it.each([
    {
      method: 'PUT',
      path: '/api/admin/questions/901',
      body: update,
      invoke: (signal: AbortSignal) => updateQuestion(901, update, signal),
    },
    {
      method: 'PATCH',
      path: '/api/admin/questions/901/archive',
      body: undefined,
      invoke: (signal: AbortSignal) => archiveQuestion(901, signal),
    },
  ])(
    'FR001_$method $path uses shared CSRF and preserves the contract body',
    async ({ method, path, body, invoke }) => {
      document.cookie = 'XSRF-TOKEN=question%20token; path=/'
      const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(json(detail))
      vi.stubGlobal('fetch', fetchMock)
      const signal = new AbortController().signal
      await expect(invoke(signal)).resolves.toEqual(detail)
      expect(fetchMock).toHaveBeenCalledOnce()
      const [url, init] = fetchMock.mock.calls[0]!
      expect(url).toBe(`${origin}${path}`)
      expect(init).toMatchObject({ method, credentials: 'include', signal })
      const headers = new Headers(init?.headers)
      expect(headers.get('X-XSRF-TOKEN')).toBe('question token')
      expect(headers.get('X-Client-Type')).toBe('WEB')
      expect(init?.body).toBe(body === undefined ? undefined : JSON.stringify(body))
      if (body) {
        expect(headers.get('Content-Type')).toBe('application/json')
        for (const field of ['qtype', 'code', 'status', 'source', 'userId']) {
          expect(JSON.parse(String(init?.body))).not.toHaveProperty(field)
        }
      }
    },
  )

  it('FR001_obtains CSRF through the shared client before a mutation when the cookie is missing', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(async (url) => {
      if (String(url) === `${origin}/api/auth/csrf`) {
        document.cookie = 'XSRF-TOKEN=issued-token; path=/'
        return new Response(null, { status: 204 })
      }
      return json(detail)
    })
    vi.stubGlobal('fetch', fetchMock)
    await updateQuestion(901, update)
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      `${origin}/api/auth/csrf`,
      `${origin}/api/admin/questions/901`,
    ])
    expect(new Headers(fetchMock.mock.calls[1]![1]?.headers).get('X-XSRF-TOKEN')).toBe(
      'issued-token',
    )
  })

  it.each([
    [401, 'UNAUTHENTICATED'],
    [403, 'FORBIDDEN'],
    [404, 'KNOWLEDGE_NODE_NOT_FOUND'],
    [404, 'QUESTION_NOT_FOUND'],
    [409, 'GRADING_CHANGE_NOT_ALLOWED'],
    [409, 'QUESTION_ARCHIVED'],
    [422, 'VALIDATION_FAILED'],
    [422, 'MALFORMED_QUESTION'],
    [422, 'KNOWLEDGE_NODE_REQUIRED'],
  ] as const)('preserves %s %s, request_id and field details', async (status, errorCode) => {
    const details = [{ field: 'primaryKid', message: 'Thông báo kiểm thử.' }]
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockResolvedValue(
        json(
          {
            error_code: errorCode,
            message: 'Thông báo an toàn.',
            request_id: 'req-question',
            details,
          },
          status,
        ),
      ),
    )
    const result =
      errorCode === 'KNOWLEDGE_NODE_NOT_FOUND' ? getKnowledgeNode('XX1-901') : getQuestion(901)
    await expect(result).rejects.toBeInstanceOf(ApiError)
    await expect(result).rejects.toMatchObject({
      status,
      errorCode,
      requestId: 'req-question',
      details,
    })
  })

  it('propagates cancellation without turning it into a network error', async () => {
    const aborted = new DOMException('Aborted', 'AbortError')
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockRejectedValue(aborted))
    const controller = new AbortController()
    controller.abort()
    await expect(getKnowledgeTree({}, controller.signal)).rejects.toBe(aborted)
  })
})
