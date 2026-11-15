import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { apiRequest } from '../../shared/api/client'

type GameInfo = {
  code: string
  name: string
  description: string
  skill: string
  howToPlay: string
  timeLimitSeconds: number
  playable: boolean
  myStats?: { bestPoints: number; roundsPlayed: number; lastPlayedAt?: string | null } | null
}

type Catalog = { status: 'loading' | 'error' } | { status: 'loaded'; games: GameInfo[] }

const levels = Array.from({ length: 9 }, (_, index) => String(index + 1))
const skillLabels: Record<string, string> = {
  VOCAB: 'Từ vựng',
  LISTENING: 'Nghe',
  READING: 'Đọc',
  CHARACTER: 'Chữ Hán',
  WRITING: 'Viết',
  GRAMMAR: 'Ngữ pháp',
  PRONUNCIATION: 'Phát âm',
}

function GameCard({ game, level }: { game: GameInfo; level: string }) {
  const path = '/play/' + encodeURIComponent(game.code) + (level ? '?hskLevel=' + level : '')
  const action = 'inline-flex min-h-touch items-center justify-center rounded-sm px-4 font-semibold'
  const stats = game.myStats
  return (
    <li>
      <article className="flex h-full min-w-0 flex-col gap-4 rounded-md border border-line bg-surface p-5">
        <div>
          <h2 className="break-words text-h3">{game.name}</h2>
          <p className="mt-1 break-words text-ink-secondary">{game.description}</p>
        </div>
        <p className="text-body-sm text-ink-secondary">
          {skillLabels[game.skill] ?? game.skill} · {game.timeLimitSeconds} giây
        </p>
        {stats && (
          <dl className="grid grid-cols-2 gap-3 rounded-sm bg-surface-subtle p-3">
            <div>
              <dt className="text-body-sm">Điểm cao nhất</dt>
              <dd className="font-bold">{stats.bestPoints}</dd>
            </div>
            <div>
              <dt className="text-body-sm">Số ván đã chơi</dt>
              <dd className="font-bold">{stats.roundsPlayed}</dd>
            </div>
          </dl>
        )}
        <details className="break-words">
          <summary className="min-h-touch cursor-pointer rounded-sm py-3 font-semibold">
            Cách chơi
          </summary>
          <p className="pb-2 text-ink-secondary">{game.howToPlay}</p>
        </details>
        <div className="mt-auto">
          {game.playable ? (
            <Link
              aria-label={'Chơi ' + game.name}
              to={path}
              className={action + ' bg-brand-800 text-surface hover:bg-brand-700'}
            >
              Chơi
            </Link>
          ) : (
            <>
              <button
                type="button"
                disabled
                aria-label={'Chơi ' + game.name}
                className={action + ' bg-surface-subtle text-ink-secondary'}
              >
                Chơi
              </button>
              <p className="mt-2 text-body-sm text-ink-secondary">Chưa đủ dữ liệu</p>
            </>
          )}
        </div>
      </article>
    </li>
  )
}

export default function GameHubPage() {
  const [catalog, setCatalog] = useState<Catalog>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [level, setLevel] = useState('')
  useEffect(() => {
    const controller = new AbortController()
    void apiRequest<GameInfo[]>('/api/community/games', { signal: controller.signal })
      .then((games) => {
        if (!controller.signal.aborted) setCatalog({ status: 'loaded', games })
      })
      .catch(() => {
        if (!controller.signal.aborted) setCatalog({ status: 'error' })
      })
    return () => controller.abort()
  }, [attempt])

  return (
    <section aria-labelledby="hub-title" className="space-y-6">
      <h1 id="hub-title" className="text-h1-sm sm:text-h1">
        Luyện tiếng Trung qua trò chơi
      </h1>
      <label className="flex w-full max-w-xs flex-col gap-2 font-semibold">
        Cấp HSK
        <select
          className="min-h-touch rounded-sm border border-line bg-surface px-3 text-ink"
          value={level}
          onChange={(event) => {
            const next = event.currentTarget.value
            if (next === '' || levels.includes(next)) setLevel(next)
          }}
        >
          <option value="">Từ tôi đang học</option>
          {levels.map((value) => (
            <option key={value} value={value}>
              HSK {value}
            </option>
          ))}
        </select>
      </label>
      {catalog.status === 'loading' && <p role="status">Đang tải trò chơi…</p>}
      {catalog.status === 'error' && (
        <div>
          <p role="alert">Chưa tải được danh mục trò chơi.</p>
          <button
            type="button"
            className="mt-2 min-h-touch rounded-sm border border-line px-4"
            onClick={() => {
              setCatalog({ status: 'loading' })
              setAttempt((value) => value + 1)
            }}
          >
            Thử lại
          </button>
        </div>
      )}
      {catalog.status === 'loaded' &&
        (catalog.games.length ? (
          <ul
            aria-label="Các trò chơi"
            className="grid min-w-0 gap-4 sm:grid-cols-2 lg:grid-cols-3"
          >
            {catalog.games.map((game) => (
              <GameCard key={game.code} game={game} level={level} />
            ))}
          </ul>
        ) : (
          <p role="status">Chưa có trò chơi.</p>
        ))}
    </section>
  )
}
