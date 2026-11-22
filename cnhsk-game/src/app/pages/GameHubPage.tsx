import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { apiRequest } from '../../shared/api/client'
import { Scene } from '../../shared/ui/arcade'
import { gameMeta, webHref } from '../../shared/ui/gameMeta'

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

/** Rounds that end on lives only carry this long safety cap. */
const UNTIMED_SECONDS = 600

const skillLabels: Record<string, string> = {
  VOCAB: 'Từ vựng',
  LISTENING: 'Nghe',
  READING: 'Đọc',
  CHARACTER: 'Chữ Hán',
  WRITING: 'Viết',
  GRAMMAR: 'Ngữ pháp',
  PRONUNCIATION: 'Phát âm',
}

function GameCard({ game, position, total }: { game: GameInfo; position: number; total: number }) {
  const meta = gameMeta(game.code)
  const path = '/play/' + encodeURIComponent(meta.slug)
  const stats = game.myStats
  const pad = (value: number) => String(value).padStart(2, '0')
  return (
    <li className="flex min-w-0">
      <article className={`game-card game-${position} min-w-0 flex-1`}>
        <div className="card-number">{pad(position + 1)} / {pad(total)}</div>
        <div className="card-symbol" aria-hidden="true">{meta.symbol}</div>
        <span className="eyebrow">{meta.eyebrow}</span>
        <h3 className="break-words">{game.name}</h3>
        <p className="break-words">{game.description}</p>
        <p className="game-card-meta">
          {skillLabels[game.skill] ?? game.skill} · {game.timeLimitSeconds >= UNTIMED_SECONDS ? 'Không tính giờ' : game.timeLimitSeconds + ' giây'}
        </p>
        {stats && (
          <dl className="game-card-stats">
            <div>
              <dt>Điểm cao nhất</dt>
              <dd>{stats.bestPoints}</dd>
            </div>
            <div>
              <dt>Số ván đã chơi</dt>
              <dd>{stats.roundsPlayed}</dd>
            </div>
          </dl>
        )}
        <details className="game-card-howto break-words">
          <summary>Cách chơi</summary>
          <p>{game.howToPlay}</p>
        </details>
        <div className="card-link">
          {game.playable ? (
            <Link aria-label={'Chơi ' + game.name} to={path}>
              Vào chơi <b aria-hidden="true">→</b>
            </Link>
          ) : (
            <span>
              <button type="button" disabled aria-label={'Chơi ' + game.name} className="game-card-locked">
                Vào chơi
              </button>
              <small>Chưa đủ dữ liệu</small>
            </span>
          )}
          <a aria-label={'Bảng xếp hạng ' + game.name} href={webHref('/leaderboards/games?game=' + game.code)}>
            Xếp hạng
          </a>
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
            Luyện tiếng Trung{' '}<br /><em>qua trò chơi</em>
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
            <span className="eyebrow">GÓC LUYỆN TẬP</span>
            <h2>Chọn cuộc hành trình</h2>
          </div>
          <p>Chọn cấp HSK ngay trong từng trò chơi. Điểm được xếp hạng riêng theo cấp.</p>
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
              {catalog.games.map((game, index) => (
                <GameCard key={game.code} game={game} position={index} total={catalog.games.length} />
              ))}
            </ul>
          ) : (
            <p role="status">Chưa có trò chơi.</p>
          ))}
      </section>
      <footer>CNHSK · Học đều một chút mỗi ngày</footer>
    </div>
  )
}
