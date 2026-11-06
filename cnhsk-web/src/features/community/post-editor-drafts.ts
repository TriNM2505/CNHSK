import { privateSessionStorageKey } from '@/shared/auth/session-private-storage'

import { POST_CATEGORIES } from './labels'
import { sanitizePostEditorOrigin, type PostFormValues } from './postForm'

export type PostEditorDraft = { fields: Partial<PostFormValues>; origin?: string }
export type PostEditorResource = number | 'new'

export function postEditorDraftKey(userId: number, resource: PostEditorResource): string {
  return privateSessionStorageKey(`${userId}:SCR-041:${resource}`)
}

function readAllowedDraft(value: unknown): PostEditorDraft | undefined {
  if (!value || typeof value !== 'object') return undefined
  const stored = value as { fields?: unknown; origin?: unknown }
  if (!stored.fields || typeof stored.fields !== 'object') return undefined
  const input = stored.fields as Record<string, unknown>
  const fields: Partial<PostFormValues> = {}
  if (typeof input.title === 'string') fields.title = input.title
  if (typeof input.content === 'string') fields.content = input.content
  if (input.category === '' || POST_CATEGORIES.some((category) => category === input.category)) fields.category = input.category as PostFormValues['category']
  const origin = sanitizePostEditorOrigin(stored.origin)
  return { fields, ...(origin ? { origin } : {}) }
}

export function readPostEditorDraft(userId: number, resource: PostEditorResource): PostEditorDraft | undefined {
  try {
    const stored = window.sessionStorage.getItem(postEditorDraftKey(userId, resource))
    return stored ? readAllowedDraft(JSON.parse(stored)) : undefined
  } catch {
    return undefined
  }
}

export function writePostEditorDraft(userId: number, resource: PostEditorResource, draft: PostEditorDraft): boolean {
  try {
    const allowed = readAllowedDraft(draft)
    if (!allowed) return false
    window.sessionStorage.setItem(postEditorDraftKey(userId, resource), JSON.stringify(allowed))
    return true
  } catch {
    return false
  }
}

export function clearPostEditorDraft(userId: number, resource: PostEditorResource): void {
  try {
    window.sessionStorage.removeItem(postEditorDraftKey(userId, resource))
  } catch {
    // Storage may become unavailable after the form was opened.
  }
}
