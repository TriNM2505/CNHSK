import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { apiRequest } from '../../shared/api/client'
import { Scene } from '../../shared/ui/arcade'
import { gameMeta, webHref } from '../../shared/ui/gameMeta'
import { GameScene } from '../../shared/ui/GameScene'

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

/** Games played to the end of their list only carry this long cap. */
const LONG_ROUND_SECONDS = 600

const skillLabels: Record<string, string> = {
  VOCAB: 'Từ vựng',
  LISTENING: 'Nghe',
  READING: 'Đọc',
  CHARACTER: 'Chữ Hán',
  WRITING: 'Viết',
  GRAMMAR: 'Ngữ pháp',
  PRONUNCIATION: 'Phát âm',
}

function GameCard({ game }: { game: GameInfo }) {
  const meta = gameMeta(game.code)
  const path = '/play/' + encodeURIComponent(meta.slug)
  const stats = game.myStats
  return (
    <li className="flex min-w-0">
      <article className="game-card min-w-0 flex-1">
        <div className="game-card-art">
          <GameScene code={game.code} />
        </div>
        <div className="game-card-body">
          <div className="game-card-title">
            <h3 className="break-words">{game.name}</h3>
            <p className="game-card-meta">
              {skillLabels[game.skill] ?? game.skill} · {game.timeLimitSeconds >= LONG_ROUND_SECONDS ? 'Tối đa 10 phút' : game.timeLimitSeconds + ' giây'}
            </p>
          </div>
          <p className="game-card-text break-words">{game.description}</p>
          <div className="game-card-actions">
            <details className="game-card-howto">
              <summary>
                <svg viewBox="0 0 20 20" aria-hidden="true"><circle cx="10" cy="10" r="8" /><path d="M7.8 7.7a2.3 2.3 0 1 1 3.2 2.1c-.7.3-1 .8-1 1.5M10 14.2v.1" /></svg>
                Cách chơi
              </summary>
              <p className="break-words">{game.howToPlay}</p>
            </details>
            {stats && (
              <dl className="game-card-stats">
                <div><dt>Kỷ lục</dt><dd>{stats.bestPoints}</dd></div>
                <div><dt>Số ván</dt><dd>{stats.roundsPlayed}</dd></div>
              </dl>
            )}
            <a className="game-card-rank" aria-label={'Bảng xếp hạng ' + game.name} title="Bảng xếp hạng"
              href={webHref('/leaderboards/games?game=' + game.code)}>
              <svg viewBox="0 0 20 20" aria-hidden="true"><path d="M6 3h8v4a4 4 0 0 1-8 0zM6 4.5H3.5c0 2.5 1 3.7 2.8 3.9M14 4.5h2.5c0 2.5-1 3.7-2.8 3.9M10 11v3M7 17h6M8 14h4v3H8z" /></svg>
            </a>
            {game.playable ? (
              <Link className="game-card-play" aria-label={'Chơi ' + game.name} to={path}>
                <svg viewBox="0 0 20 20" aria-hidden="true"><path d="M6 4.2v11.6L16 10z" /></svg>
                Chơi
              </Link>
            ) : (
              <span className="game-card-locked">
                <small>Chưa đủ dữ liệu</small>
                <button type="button" disabled aria-label={'Chơi ' + game.name} className="game-card-play">Chơi</button>
              </span>
            )}
          </div>
        </div>
      </article>
    </li>
  )
}

export default function GameHubPage() {
  const [catalog, setCatalog] = useState<Catalog>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
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

  const count = catalog.status === 'loaded' ? catalog.games.length : 6
  return (
    <div className="home">
      <section className="hero" aria-labelledby="hub-title">
        <Scene />
        <div className="hero-copy">
          <span className="eyebrow">CHƠI ĐỂ NHỚ · HỌC ĐỂ VUI</span>
          <h1 id="hub-title">
            <span>Luyện tiếng Trung</span>{' '}<em>qua trò chơi</em>
          </h1>
          <p>Sáu trò chơi luyện chữ Hán, pinyin, bộ thủ và từ vựng. Điểm của bạn được lưu và xếp hạng cùng mọi người.</p>
          <a href="#games" className="primary-button">Khám phá trò chơi <span aria-hidden="true">↗</span></a>
        </div>
        <div className="hero-count" aria-hidden="true">
          <b>{String(count).padStart(2, '0')}</b><span>TRÒ CHƠI<br />ĐANG MỞ</span>
        </div>
      </section>
      <section id="games" className="games-section" aria-label="Góc luyện tập">
        <div className="section-heading">
          <div>
            <h2>Chọn <em>trò chơi</em> của bạn</h2>
            <p>Chọn cấp HSK ngay trong từng trò chơi. Điểm được xếp hạng riêng theo cấp.</p>
          </div>
          <a className="section-link" href={webHref('/leaderboards/games')}>
            <svg viewBox="0 0 20 20" aria-hidden="true"><path d="M6 3h8v4a4 4 0 0 1-8 0zM6 4.5H3.5c0 2.5 1 3.7 2.8 3.9M14 4.5h2.5c0 2.5-1 3.7-2.8 3.9M10 11v3M7 17h6M8 14h4v3H8z" /></svg>
            Bảng xếp hạng
          </a>
        </div>
        {catalog.status === 'loading' && <p role="status">Đang tải trò chơi…</p>}
        {catalog.status === 'error' && (
          <div>
            <p role="alert">Chưa tải được danh mục trò chơi.</p>
            <button
              type="button"
              className="secondary-button"
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
            <ul aria-label="Các trò chơi" className="game-grid">
              {catalog.games.map((game) => <GameCard key={game.code} game={game} />)}
            </ul>
          ) : (
            <p role="status">Chưa có trò chơi.</p>
          ))}
      </section>
      <footer>CNHSK · Học đều một chút mỗi ngày</footer>
    </div>
  )
}
