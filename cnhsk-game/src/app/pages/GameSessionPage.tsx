import { Link, useParams } from 'react-router-dom'

export default function GameSessionPage() {
  const { code } = useParams()

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
      <Link
        to="/"
        className="mt-8 inline-flex min-h-touch items-center justify-center rounded-sm bg-brand-800 px-5 py-3 font-semibold text-surface hover:bg-brand-700"
      >
        Về danh sách trò chơi
      </Link>
    </section>
  )
}
