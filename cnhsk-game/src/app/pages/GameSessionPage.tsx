import { Link, useLocation, useParams } from 'react-router-dom'

import { gameLoginHref, useCurrentUser } from '../../shared/auth/useCurrentUser'

export default function GameSessionPage() {
  const { code } = useParams()
  const location = useLocation()
  const session = useCurrentUser()

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
