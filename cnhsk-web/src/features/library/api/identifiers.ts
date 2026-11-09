import { ApiError, invalidResponseError } from '@/shared/api'

export function isLibraryIdentifier(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

export function identifierPath(value: unknown): string {
  if (!isLibraryIdentifier(value))
    throw new ApiError({
      status: 422,
      errorCode: 'VALIDATION_FAILED',
      message: 'ID phải là số nguyên dương trong miền số an toàn.',
    })
  return encodeURIComponent(String(value))
}

export function checkBodyIdentifiers(body: { lexemeId?: number | null; topicId?: number | null }) {
  for (const id of [body.lexemeId, body.topicId]) {
    if (id !== null && id !== undefined) identifierPath(id)
  }
}

function checkResponse(value: unknown): void {
  if (Array.isArray(value)) {
    value.forEach(checkResponse)
    return
  }
  if (typeof value !== 'object' || value === null) return
  const record = value as Record<string, unknown>
  for (const key of ['id', 'lexemeId', 'cardId']) {
    if (!(key in record)) continue
    if (key === 'lexemeId' && record[key] === null) continue
    if (!isLibraryIdentifier(record[key])) throw invalidResponseError()
  }
  for (const key of ['items', 'cards', 'segments', 'components', 'containedIn', 'related']) {
    if (Array.isArray(record[key])) checkResponse(record[key])
  }
}

export async function checkedIdentifiers<T>(response: Promise<T>): Promise<T> {
  const value = await response
  checkResponse(value)
  return value
}
