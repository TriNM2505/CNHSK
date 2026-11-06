export const PRIVATE_SESSION_STORAGE_PREFIX = 'cnhsk:private-session:'

export function privateSessionStorageKey(suffix: string): string {
  return `${PRIVATE_SESSION_STORAGE_PREFIX}${suffix}`
}

export function clearPrivateSessionStorage(): void {
  try {
    const storage = window.sessionStorage
    const keys: string[] = []
    for (let index = 0; index < storage.length; index += 1) {
      const key = storage.key(index)
      if (key?.startsWith(PRIVATE_SESSION_STORAGE_PREFIX)) keys.push(key)
    }
    for (const key of keys) storage.removeItem(key)
  } catch {
    // Browser storage restrictions must not prevent the auth transition.
  }
}
