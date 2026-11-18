import { Link } from 'react-router-dom'

export function ModerationNavigation({ active }: { active?: 'posts' | 'reports' }) {
  return (
    <nav
      aria-label="Kiểm duyệt cộng đồng"
      className="mb-6 flex flex-wrap gap-3 border-b border-line pb-3"
    >
      <Link
        aria-current={active === 'posts' ? 'page' : undefined}
        className={`inline-flex min-h-touch items-center rounded-sm px-4 py-2 text-body-sm font-semibold transition-colors ${
          active === 'posts'
            ? 'bg-brand-600 text-surface'
            : 'bg-surface text-brand-700 underline hover:bg-brand-50'
        }`}
        to="/moderation/posts"
      >
        Bài chờ duyệt
      </Link>
      <Link
        aria-current={active === 'reports' ? 'page' : undefined}
        className={`inline-flex min-h-touch items-center rounded-sm px-4 py-2 text-body-sm font-semibold transition-colors ${
          active === 'reports'
            ? 'bg-brand-600 text-surface'
            : 'bg-surface text-brand-700 underline hover:bg-brand-50'
        }`}
        to="/moderation/reports"
      >
        Báo cáo vi phạm
      </Link>
    </nav>
  )
}
