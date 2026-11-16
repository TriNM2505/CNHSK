import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { loadStrokeData } from './strokeData'

const example = {
  strokes: ['M 10 10 L 20 20'],
  medians: [[[10, 10], [20, 20]]],
}

function json(input: unknown, status = 200) {
  return new Response(JSON.stringify(input), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8' },
  })
}

describe('loadStrokeData', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    vi.spyOn(console, 'warn').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('loads one encoded character from the app and uses a cache for parallel and later calls', async () => {
    fetchMock.mockResolvedValue(json(example))

    const first = loadStrokeData('一')
    const second = loadStrokeData('一')
    expect(first).toBe(second)
    await expect(first).resolves.toEqual(example)
    await expect(loadStrokeData('一')).resolves.toEqual(example)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledWith('/stroke-data/%E4%B8%80.json')
  })

  it('supports a single Han character outside the BMP', async () => {
    fetchMock.mockResolvedValue(json(example))
    await expect(loadStrokeData('𠀀')).resolves.toEqual(example)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledWith('/stroke-data/%F0%A0%80%80.json')
  })

  it.each([
    ['404', '丁', () => json({}, 404)],
    ['HTML 200', '七', () => new Response('<!doctype html>', { headers: { 'content-type': 'text/html' } })],
    ['missing medians', '三', () => json({ strokes: example.strokes })],
    ['invalid median', '山', () => json({ strokes: example.strokes, medians: [[['x', 20]]] })],
    ['invalid JSON', '水', () => new Response('{', { headers: { 'content-type': 'application/json' } })],
  ] as const)('treats %s as missing data', async (_label, character, response) => {
    fetchMock.mockResolvedValue(response())
    await expect(loadStrokeData(character)).resolves.toBeNull()
    expect(console.warn).toHaveBeenCalledTimes(1)
  })

  it('treats network failure as missing data and allows a fresh request', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce(json(example))

    await expect(loadStrokeData('木')).resolves.toBeNull()
    expect(console.warn).toHaveBeenCalledTimes(1)
    await expect(loadStrokeData('木')).resolves.toEqual(example)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it.each(['', '你好', 'a', '../木'])('rejects non-character input %s before fetching', async (input) => {
    await expect(loadStrokeData(input)).resolves.toBeNull()
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
