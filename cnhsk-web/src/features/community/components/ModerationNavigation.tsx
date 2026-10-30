import { Link } from 'react-router-dom'

export function ModerationNavigation({ active }: { active?: 'posts' | 'reports' }) {
  return (
    <nav
      aria-label="Kiểm duyệt cộng đồng"
      className="mb-6 flex flex-wrap gap-4 border-b border-line pb-3"
    >
      <Link
        aria-current={active === 'posts' ? 'page' : undefined}
        className="min-h-touch py-2 text-brand-700 underline"
        to="/moderation/posts"
      >
        Bài chờ duyệt
      </Link>
      <Link
        aria-current={active === 'reports' ? 'page' : undefined}
        className="min-h-touch py-2 text-brand-700 underline"
        to="/moderation/reports"
      >
        Báo cáo vi phạm
      </Link>
    </nav>
  )
}
