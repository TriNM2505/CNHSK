import type { ApiQueryState } from '@/shared/api/useApiQuery'
import { Button, EmptyState, ErrorState, PageHeader, Skeleton } from '@/shared/ui'

import { formatNumber, formatTime } from '../settingsForm'
import type { AiUsage, SettingItem } from '../types'
import { SettingEditor, type SettingEditorProps } from './SettingEditor'
import { SettingHistory, type SettingHistoryProps } from './SettingHistory'

export type SettingsViewProps = {
  state: ApiQueryState
  items: readonly SettingItem[]
  error?: { message: string; requestId?: string }
  onRetry: () => void
  onEdit: (item: SettingItem, mode: 'edit' | 'reset') => void
  onHistory: (key: string) => void
  pending: boolean
  editor?: SettingEditorProps
  history?: SettingHistoryProps
  message?: string
  usage: { state: ApiQueryState; data?: AiUsage; onRetry: () => void }
}

export function SettingsView({
  state,
  items,
  error,
  onRetry,
  onEdit,
  onHistory,
  pending,
  editor,
  history,
  message,
  usage,
}: SettingsViewProps) {
  const groups = new Map<string, SettingItem[]>()
  for (const item of items) groups.set(item.group, [...(groups.get(item.group) ?? []), item])
  const usageData = usage.data
  const readableUsage =
    usageData?.available &&
    usageData.used !== null &&
    Number.isSafeInteger(usageData.used) &&
    usageData.used >= 0
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 p-4 sm:p-6">
      <PageHeader
        title="Cài đặt hệ thống"
        subtitle="Điều chỉnh các tham số nghiệp vụ trong phạm vi được phép. Mọi thay đổi đều cần lý do và được lưu lịch sử."
      />
      <Button size="sm" variant="outline" disabled={pending} onClick={onRetry}>
        Tải lại cài đặt
      </Button>
      <p className="text-body-sm text-ink-secondary">
        Thay đổi có hiệu lực trên máy chủ vừa lưu; các máy chủ khác cập nhật trong tối đa 30 giây.
        Đổi hạn mức không đặt lại số lượt hoặc token đã dùng.
      </p>
      {message ? (
        <p role="status" className="rounded-sm border border-line bg-brand-50 p-3">
          {message}
        </p>
      ) : null}
      {state === 'loading' ? (
        <Skeleton className="h-56" />
      ) : state === 'error' ? (
        <ErrorState
          message={error?.message ?? 'Không thể tải cài đặt.'}
          requestId={error?.requestId}
          onRetry={onRetry}
        />
      ) : !items.length ? (
        <EmptyState title="Chưa có cài đặt" description="Chưa có tham số được phép thay đổi." />
      ) : (
        Array.from(groups, ([group, rows]) => (
          <section aria-label={group} className="min-w-0 space-y-3" key={group}>
            <h2 className="text-h2">{group}</h2>
            <div className="space-y-3">
              {rows.map((item) => (
                <article
                  aria-label={item.key}
                  key={item.key}
                  className="min-w-0 rounded-md border border-line bg-surface p-4"
                >
                  <div className="grid min-w-0 gap-4 lg:grid-cols-[minmax(0,1fr)_auto]">
                    <div className="min-w-0 space-y-1">
                      <h3 className="break-all font-semibold">{item.key}</h3>
                      <p className="break-words">{item.description}</p>
                      <p className="text-body-sm text-ink-secondary">
                        Số nguyên · Từ {formatNumber(item.min)} đến {formatNumber(item.max)}
                      </p>
                      <dl className="flex flex-wrap gap-x-6 gap-y-2 pt-2">
                        <div>
                          <dt className="text-caption text-ink-muted">Hiện tại</dt>
                          <dd className="text-h3 tabular-nums">
                            {formatNumber(item.currentValue)}
                          </dd>
                        </div>
                        <div>
                          <dt className="text-caption text-ink-muted">Mặc định</dt>
                          <dd className="font-semibold tabular-nums">
                            {formatNumber(item.defaultValue)}
                          </dd>
                        </div>
                      </dl>
                      <p className="text-body-sm text-ink-secondary">
                        {item.overridden ? 'Đang ghi đè' : 'Đang dùng mặc định'}
                      </p>
                      {item.updatedAt ? (
                        <p className="break-words text-caption text-ink-muted">
                          Cập nhật:{' '}
                          {item.updatedBy?.displayName ||
                            (item.updatedBy
                              ? `Người dùng #${item.updatedBy.id}`
                              : 'Không có thông tin người sửa')}{' '}
                          · {formatTime(item.updatedAt)}
                        </p>
                      ) : null}
                    </div>
                    <div className="flex flex-wrap items-start gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={pending}
                        aria-label={`Sửa ${item.key}`}
                        onClick={() => onEdit(item, 'edit')}
                      >
                        Sửa
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={pending}
                        aria-label={`Về mặc định ${item.key}`}
                        onClick={() => onEdit(item, 'reset')}
                      >
                        Về mặc định
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        disabled={pending}
                        aria-label={`Lịch sử ${item.key}`}
                        onClick={() => onHistory(item.key)}
                      >
                        Lịch sử
                      </Button>
                    </div>
                  </div>
                  {editor?.item.key === item.key ? <SettingEditor {...editor} /> : null}
                  {history?.settingKey === item.key ? <SettingHistory {...history} /> : null}
                </article>
              ))}
            </div>
          </section>
        ))
      )}
      <footer
        aria-label="Số liệu token"
        className="space-y-2 rounded-md border border-line bg-surface p-4"
      >
        <h2 className="text-h3">Token AI đã dùng tháng này</h2>
        {usage.state === 'loading' ? (
          <p role="status">Đang tải số liệu…</p>
        ) : usage.state === 'error' || !usageData ? (
          <p>Chưa có dữ liệu</p>
        ) : !readableUsage ? (
          <p>Chưa đọc được số liệu</p>
        ) : (
          <p aria-label="Token AI đã dùng tháng này" className="break-all text-h2 tabular-nums">
            {formatNumber(usageData.used!)} / {formatNumber(usageData.budget)}
          </p>
        )}
        {usage.state === 'ready' && usageData ? (
          <p className="text-caption text-ink-muted">
            Tháng {usageData.month} · Giờ Việt Nam · Chỉ đọc
          </p>
        ) : null}
        {usage.state !== 'loading' ? (
          <Button size="sm" variant="ghost" onClick={usage.onRetry}>
            Tải lại số liệu token
          </Button>
        ) : null}
      </footer>
    </main>
  )
}
