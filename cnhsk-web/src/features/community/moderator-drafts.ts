export const MODERATOR_DRAFT_PREFIX = 'cnhsk:spec009:t18:draft:'
export const MODERATOR_DRAFT_TTL_MS = 7_200_000
export type ModeratorDraftIdentity = { userId: number; screenId: 'SCR-061' | 'SCR-063'; resourceId: number; field: string }

export function moderatorDraftKey({ userId, screenId, resourceId, field }: ModeratorDraftIdentity): string {
  return `${MODERATOR_DRAFT_PREFIX}${userId}:${screenId}:${resourceId}:${encodeURIComponent(field)}`
}

function storage(): Storage | undefined {
  try { return window.sessionStorage } catch { return undefined }
}
function remove(store: Storage, key: string) {
  try { store.removeItem(key) } catch { /* Storage may be disabled; in-memory editing remains available. */ }
}
function parse(value: string | null, now: number): { text: string; savedAt: number } | undefined {
  if (!value) return undefined
  try {
    const entry: unknown = JSON.parse(value)
    if (typeof entry !== 'object' || entry === null || !('text' in entry) || typeof entry.text !== 'string' || !('savedAt' in entry) || typeof entry.savedAt !== 'number') return undefined
    if (Object.keys(entry).length !== 2 || !Number.isFinite(entry.savedAt) || entry.savedAt < 0 || entry.savedAt > now || now - entry.savedAt >= MODERATOR_DRAFT_TTL_MS) return undefined
    return { text: entry.text, savedAt: entry.savedAt }
  } catch { return undefined }
}

// Called only after the feature's authenticated MODERATOR gate has mounted.
export function cleanModeratorDrafts(userId: number, now = Date.now()): void {
  const store = storage()
  if (!store) return
  try {
    const keys: string[] = []
    for (let i = 0; i < store.length; i++) {
      const key = store.key(i)
      if (key?.startsWith(MODERATOR_DRAFT_PREFIX)) keys.push(key)
    }
    for (const key of keys) {
      const owner = key.slice(MODERATOR_DRAFT_PREFIX.length).split(':')[0]
      if (owner !== String(userId)) { remove(store, key); continue }
      if (!parse(store.getItem(key), now)) remove(store, key)
    }
  } catch { /* Access can be blocked independently of writes. */ }
}

export function readModeratorDraft(identity: ModeratorDraftIdentity, now = Date.now()): string {
  const store = storage()
  if (!store) return ''
  const key = moderatorDraftKey(identity)
  try {
    const raw = store.getItem(key)
    const entry = parse(raw, now)
    if (!entry && raw !== null) remove(store, key)
    return entry?.text ?? ''
  } catch { return '' }
}
export function writeModeratorDraft(identity: ModeratorDraftIdentity, text: string): void {
  try { storage()?.setItem(moderatorDraftKey(identity), JSON.stringify({ text, savedAt: Date.now() })) } catch { /* Continue editing in memory. */ }
}
export function removeModeratorDraft(identity: ModeratorDraftIdentity): void {
  const store = storage()
  if (store) remove(store, moderatorDraftKey(identity))
}
