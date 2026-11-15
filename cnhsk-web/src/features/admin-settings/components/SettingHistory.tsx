import type { ApiQueryState } from '@/shared/api/useApiQuery'
import { Button, EmptyState, ErrorState, Pagination, Skeleton } from '@/shared/ui'

import { formatNumber, formatTime } from '../settingsForm'
import type { SettingHistoryPage } from '../types'

export type SettingHistoryProps = {
  settingKey: string
  state: ApiQueryState
  data?: SettingHistoryPage
  error?: { message: string; requestId?: string }
  pending?: boolean
  onPage: (page: number) => void
  onRetry: () => void
  onClose: () => void
}

export function SettingHistory({
  settingKey,
  state,
  data,
  error,
  pending,
  onPage,
  onRetry,
  onClose,
}: SettingHistoryProps) {
  return (
    <section
      aria-label={`Lịch sử ${settingKey}`}
      className="mt-4 space-y-4 border-t border-line pt-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-h3">Lịch sử thay đổi</h3>
        <Button size="sm" variant="ghost" disabled={pending} onClick={onClose}>
          Đóng lịch sử
        </Button>
      </div>
      {state === 'loading' ? (
        <Skeleton className="h-32" />
      ) : state === 'error' ? (
        <ErrorState
          message={error?.message ?? 'Không thể tải lịch sử.'}
          requestId={error?.requestId}
          onRetry={onRetry}
        />
      ) : !data?.items.length ? (
        <EmptyState
          title="Chưa có lịch sử"
          description="Các lần sửa và về mặc định sẽ được ghi tại đây."
        />
      ) : (
        <>
          <ol className="space-y-3">
            {data.items.map((entry, index) => (
              <li key={`${data.page}-${index}`} className="space-y-1 rounded-sm bg-canvas p-3">
                <p className="font-semibold">
                  {entry.oldValue === null
                    ? 'Không có giá trị trước'
                    : formatNumber(entry.oldValue)}{' '}
                  → {entry.newValue === null ? 'Về mặc định' : formatNumber(entry.newValue)}
                </p>
                <p className="whitespace-pre-wrap break-words">{entry.reason}</p>
                <p className="break-words text-caption text-ink-muted">
                  {entry.actor.displayName || `Người dùng #${entry.actor.id}`} ·{' '}
                  {formatTime(entry.at)}
                </p>
              </li>
            ))}
          </ol>
          <div
            aria-disabled={pending || undefined}
            className={pending ? 'pointer-events-none opacity-60' : undefined}
          >
            <Pagination
              label={`Phân trang lịch sử ${settingKey}`}
              page={data.page + 1}
              size={data.size}
              total={data.totalElements}
              onChange={(page) => {
                if (!pending) onPage(page - 1)
              }}
            />
          </div>
        </>
      )}
    </section>
  )
}
