/** Where an old `/learn/pronunciation…` link now lives (Nghe or Luyện nói), or undefined for current paths. */
export function currentPronunciationPath(pathname: string): string | undefined {
  const path = pathname.replace(/\/$/, '')
  if (!path.startsWith('/learn/pronunciation')) return undefined
  if (path === '/learn/pronunciation/speaking') return '/learn/speaking'
  return path.replace('/learn/pronunciation', '/learn/listening')
}
