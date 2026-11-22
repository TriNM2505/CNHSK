export interface StrokeData {
  readonly strokes: readonly string[]
  readonly medians: readonly (readonly (readonly [number, number])[])[]
}

const cache = new Map<string, Promise<StrokeData | null>>()
const hanCharacter = /^\p{Script=Han}$/u

function isStrokeData(value: unknown): value is StrokeData {
  if (typeof value !== 'object' || value === null || !('strokes' in value) || !('medians' in value)) {
    return false
  }

  const { strokes, medians } = value
  return Array.isArray(strokes)
    && strokes.length > 0
    && strokes.every((stroke) => typeof stroke === 'string' && stroke.length > 0)
    && Array.isArray(medians)
    && medians.length === strokes.length
    && medians.every((median) => Array.isArray(median) && median.length >= 2 && median.every(
      (point) => Array.isArray(point) && point.length === 2 && point.every(Number.isFinite),
    ))
}

const RETRY_MS = 1000
const ATTEMPTS = 5

/** A dropped connection is retried: without this, one short drop would skip every remaining character. */
async function fetchWithRetry(url: string): Promise<Response> {
  for (let attempt = 1; ; attempt += 1) {
    try {
      return await fetch(url)
    } catch (error) {
      if (attempt === ATTEMPTS) throw error
      await new Promise((resolve) => { setTimeout(resolve, RETRY_MS) })
    }
  }
}

async function fetchStrokeData(character: string): Promise<StrokeData | null> {
  try {
    const response = await fetchWithRetry(`/stroke-data/${encodeURIComponent(character)}.json`)
    const contentType = response.headers.get('content-type')?.split(';', 1)[0]?.trim().toLowerCase()
    if (response.ok && contentType === 'application/json') {
      const data: unknown = await response.json()
      if (isStrokeData(data)) return data
    }
  } catch {
    // Missing, unreadable or unavailable data all follow the game’s missed-item path.
  }

  console.warn('Không tải được dữ liệu nét chữ.', character)
  return null
}

/** Returns null for the caller to treat as a missed item (spec 008 E7). */
export function loadStrokeData(character: string): Promise<StrokeData | null> {
  if (!hanCharacter.test(character)) return Promise.resolve(null)

  const cached = cache.get(character)
  if (cached) return cached

  const pending = fetchStrokeData(character).then((data) => {
    if (data === null) cache.delete(character)
    return data
  })
  cache.set(character, pending)
  return pending
}
