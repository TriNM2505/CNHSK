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
      className="mt-4 min-w-0 space-y-2 rounded-sm border border-line bg-canvas p-3 text-body-sm"
      role="status"
    >
      <p className="flex items-center gap-2 font-semibold">
        <LockKeyhole aria-hidden="true" className="size-4 shrink-0" />
        Đang khoá
      </p>
      <p className="break-words">
        Cần đạt 90% bài kiểm tra:{' '}
        {lockedBy.map((topic, index) => (
          <span key={topic.id}>
            {index > 0 ? ', ' : ''}
            <Link className="text-brand-700 underline" to={`/learn/topics/${topic.id}`}>
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
