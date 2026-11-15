import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <section
      aria-labelledby="not-found-title"
      className="mx-auto max-w-reading rounded-lg border border-line bg-surface p-6 text-center sm:p-10"
    >
      <p aria-hidden="true" className="text-display-sm font-bold text-brand-800">
        404
      </p>
      <h1 id="not-found-title" className="mt-4 text-h1-sm sm:text-h1">
        Không tìm thấy trang
      </h1>
      <p className="mt-4 text-ink-secondary">Bạn có thể trở về danh sách trò chơi để tiếp tục.</p>
      <Link
        to="/"
        className="mt-8 inline-flex min-h-touch items-center justify-center rounded-sm bg-brand-800 px-5 py-3 font-semibold text-surface hover:bg-brand-700"
      >
        Về danh sách trò chơi
      </Link>
    </section>
  )
}
