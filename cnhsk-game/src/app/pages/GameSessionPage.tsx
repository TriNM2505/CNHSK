import { lazy, Suspense } from 'react'
import type { ComponentType } from 'react'
import { Link, Navigate, useLocation, useParams } from 'react-router-dom'

import { gameLoginHref, useCurrentUser } from '../../shared/auth/useCurrentUser'
import { GameShell } from '../../shared/ui/GameShell'
import type { GameProps } from '../../shared/ui/GameShell'
import NotFoundPage from './NotFoundPage'

async function readPinyin(id: number, signal: AbortSignal) {
  const base = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? ''
  // The lookup is public and bounded by the feedback window; never try to refresh the session here.
  const response = await fetch(base + '/api/public/dictionary/lexemes/' + id, { credentials: 'omit', signal })
  if (!response.ok) return null
  const detail: { pinyin?: string | null } = await response.json()
  return detail.pinyin ?? null
}

const games = new Map<string, { code: string; Game: ComponentType<GameProps> }>([
  ['char-rain', { code: 'CHAR_RAIN', Game: lazy(() => import('../../games/char-rain/CharRainGame').then(
    ({ CharRainGame }) => ({ default: CharRainGame }))) }],
  ['pinyin-match', { code: 'PINYIN_MATCH', Game: lazy(() => import('../../games/pinyin-match/PinyinMatchGame').then(
    ({ PinyinMatchGame }) => ({ default: PinyinMatchGame }))) }],
  ['radical-match', { code: 'RADICAL_MATCH', Game: lazy(() => import('../../games/radical-match/RadicalMatchGame').then(
    ({ RadicalMatchGame }) => ({ default: RadicalMatchGame }))) }],
  ['char-catch', { code: 'CHAR_CATCH', Game: lazy(() => import('../../games/char-catch/CharCatchGame').then(
    ({ CharCatchGame }) => ({ default: CharCatchGame }))) }],
  ['brush-stroke', { code: 'BRUSH_STROKE', Game: lazy(() => import('../../games/brush-stroke/BrushStrokeGame').then(
    ({ BrushStrokeGame }) => ({ default: (props: GameProps) => <BrushStrokeGame {...props} readPinyin={readPinyin} /> }))) }],
  ['flying-dart', { code: 'FLYING_DART', Game: lazy(() => import('../../games/flying-dart/FlyingDartGame').then(
    ({ FlyingDartGame }) => ({ default: (props: GameProps) => <FlyingDartGame {...props} readPinyin={readPinyin} /> }))) }],
])

export default function GameSessionPage() {
  const { code } = useParams()
  const game = games.get(code ?? '')
  if (game) return <GameSession {...game} />
  const legacy = [...games].find(([, game]) => game.code === code)
  if (legacy) return <LegacyGameRedirect slug={legacy[0]} />
  return <NotFoundPage />
}

export function LegacyGameRedirect({ slug }: { slug: string }) {
  const { search, hash } = useLocation()
  return <Navigate replace to={{ pathname: '/play/' + slug, search, hash }} />
}

function GameSession({ code, Game }: { code: string; Game: ComponentType<GameProps> }) {
  const location = useLocation()
  const session = useCurrentUser()
  const level = new URLSearchParams(location.search).get('hskLevel')
  if (session.status === 'authenticated') {
    return (
      <Suspense fallback={<p role="status">Đang tải trò chơi…</p>}>
        <GameShell code={code} Game={Game} hskLevel={level && /^[1-9]$/.test(level) ? Number(level) : undefined} />
      </Suspense>
    )
  }

  return (
    <section
      aria-labelledby="session-title"
      data-game-code={code}
      className="mx-auto max-w-reading rounded-lg border border-line bg-surface p-6 sm:p-10"
    >
      <p className="text-label text-brand-700">Game Box</p>
      <h1 id="session-title" className="mt-3 text-h1-sm sm:text-h1">
        Ván chơi
      </h1>
      <p className="mt-4 text-ink-secondary">
        Một khoảng thời gian nhỏ để tập trung luyện tiếng Trung.
      </p>
      {session.status === 'loading' && <p role="status">Đang kiểm tra phiên đăng nhập…</p>}
      {session.status === 'error' && <p role="alert">Chưa kiểm tra được phiên đăng nhập.</p>}
      {session.status === 'guest' && (
        <a
          href={gameLoginHref(location.pathname + location.search + location.hash)}
          className="mt-5 flex min-h-touch w-fit items-center rounded-sm border border-brand-800 px-5 py-3 font-semibold text-brand-800"
        >
          Đăng nhập để chơi
        </a>
      )}
      <Link
        to="/"
        className="mt-8 inline-flex min-h-touch items-center justify-center rounded-sm bg-brand-800 px-5 py-3 font-semibold text-surface hover:bg-brand-700"
      >
        Về danh sách trò chơi
      </Link>
    </section>
  )
}
