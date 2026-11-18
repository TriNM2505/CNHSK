import { LockKeyhole } from 'lucide-react'
import { Link } from 'react-router-dom'

import type { LockedTopicRef } from '../types'

export function TopicLockNotice({
  lockedBy,
  requestId,
}: {
  lockedBy: readonly LockedTopicRef[]
  requestId?: string
}) {
  return (
    <div
      className="mt-4 min-w-0 space-y-2 rounded-md border border-brand-100 border-l-4 border-l-brand-600 bg-brand-50 p-3 text-body-sm text-ink-secondary"
      role="status"
    >
      <p className="flex items-center gap-2 font-semibold text-brand-700">
        <LockKeyhole aria-hidden="true" className="size-4 shrink-0" />
        Đang khoá
      </p>
      <p className="break-words">
        Cần đạt 90% bài kiểm tra:{' '}
        {lockedBy.map((topic, index) => (
          <span key={topic.id}>
            {index > 0 ? ', ' : ''}
            <Link
              className="rounded-sm text-brand-700 underline underline-offset-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
              to={`/learn/topics/${topic.id}`}
            >
              {topic.name}
            </Link>
          </span>
        ))}
        {lockedBy.length === 0 ? 'các chủ đề tiên quyết.' : null}
      </p>
      {requestId ? (
        <p className="break-all text-caption text-ink-muted">Mã yêu cầu: {requestId}</p>
      ) : null}
    </div>
  )
}
