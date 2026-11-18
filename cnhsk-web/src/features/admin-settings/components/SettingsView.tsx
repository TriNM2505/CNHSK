import type { ApiQueryState } from '@/shared/api/useApiQuery'
import { Button, EmptyState, ErrorState, PageBanner, Skeleton, StatusBadge } from '@/shared/ui'

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
      <PageBanner
        title="Cài đặt hệ thống"
        subtitle="Điều chỉnh các tham số nghiệp vụ trong phạm vi được phép. Mọi thay đổi đều cần lý do và được lưu lịch sử."
      />
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-line bg-surface p-4 sm:px-5">
        <p className="max-w-3xl text-body-sm text-ink-secondary">
          Thay đổi có hiệu lực trên máy chủ vừa lưu; các máy chủ khác cập nhật trong tối đa 30 giây.
          Đổi hạn mức không đặt lại số lượt hoặc token đã dùng.
        </p>
        <Button
          className="shrink-0"
          size="sm"
          variant="outline"
          disabled={pending}
          onClick={onRetry}
        >
          Tải lại cài đặt
        </Button>
      </div>
      {message ? (
        <p
          role="status"
          className="rounded-sm border border-line bg-brand-50 px-4 py-3 text-body-sm text-ink"
        >
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
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-h2">{group}</h2>
              <span className="rounded-full border border-line bg-surface px-2.5 py-0.5 text-caption font-semibold text-ink-secondary">
                {rows.length} tham số
              </span>
            </div>
            <div className="space-y-3">
              {rows.map((item) => (
                <article
                  aria-label={item.key}
                  key={item.key}
                  className="min-w-0 rounded-md border border-line bg-surface p-4 sm:p-5"
                >
                  <div className="grid min-w-0 gap-4 lg:grid-cols-[minmax(0,1fr)_auto]">
                    <div className="min-w-0 space-y-1.5">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="break-all font-mono text-body-sm font-bold text-ink">
                          {item.key}
                        </h3>
                        <StatusBadge tone={item.overridden ? 'warning' : 'neutral'}>
                          {item.overridden ? 'Đang ghi đè' : 'Đang dùng mặc định'}
                        </StatusBadge>
                      </div>
                      <p className="break-words text-body text-ink">{item.description}</p>
                      <p className="text-body-sm text-ink-secondary">
                        Số nguyên · Từ {formatNumber(item.min)} đến {formatNumber(item.max)}
                      </p>
                      <dl className="mt-3 flex flex-wrap gap-x-6 gap-y-2 rounded-sm border border-line bg-canvas px-4 py-3">
                        <div>
                          <dt className="text-caption font-semibold text-ink-muted">Hiện tại</dt>
                          <dd className="text-h3 tabular-nums text-ink">
                            {formatNumber(item.currentValue)}
                          </dd>
                        </div>
                        <div>
                          <dt className="text-caption font-semibold text-ink-muted">Mặc định</dt>
                          <dd className="font-semibold tabular-nums text-ink-secondary">
                            {formatNumber(item.defaultValue)}
                          </dd>
                        </div>
                      </dl>
                      {item.updatedAt ? (
                        <p className="break-words pt-1 text-caption text-ink-muted">
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
        className="flex flex-wrap items-center justify-between gap-4 rounded-md border border-line bg-surface p-4 sm:p-5"
      >
        <div className="min-w-0 space-y-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-h3">Token AI đã dùng tháng này</h2>
            <StatusBadge tone="neutral">Chỉ đọc</StatusBadge>
          </div>
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
        </div>
        {usage.state !== 'loading' ? (
          <Button size="sm" variant="outline" onClick={usage.onRetry}>
            Tải lại số liệu token
          </Button>
        ) : null}
      </footer>
    </main>
  )
}
