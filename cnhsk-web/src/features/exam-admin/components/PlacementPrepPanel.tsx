import { useRef, useState } from 'react'

import { toApiError, useApiQuery } from '@/shared/api'
import { Button, ConfirmPanel, StatusBadge } from '@/shared/ui'

import { buildPlacementForms, getPlacementPool } from '../api/placement'

const statusLabels: Record<string, string> = {
  DRAFT: 'Nháp',
  PUBLISHED: 'Đã xuất bản',
  ARCHIVED: 'Đã lưu trữ',
}

function failureText(caught: unknown): string {
  const error = toApiError(caught)
  if (error.errorCode === 'PLACEMENT_BANK_INSUFFICIENT') {
    const details = Array.isArray(error.details) ? error.details : []
    const missing = details.flatMap((detail: unknown) => {
      if (!detail || typeof detail !== 'object') return []
      const { level, available, needed } = detail as Record<string, unknown>
      return typeof level === 'number' &&
        typeof available === 'number' &&
        typeof needed === 'number'
        ? [`HSK ${level} mới có ${available}/${needed} câu`]
        : []
    })
    return missing.length
      ? `Chưa đủ câu đã duyệt: ${missing.join('; ')}.`
      : 'Chưa đủ câu đã nhập cho bài kiểm tra trình độ.'
  }
  return error.message || 'Chưa thực hiện được. Vui lòng thử lại.'
}

/**
 * Content editors prepare the placement test here. Questions are imported by hand from CSV with codes PLC1-, PLC2-
 * and PLC3- (no AI); once each level has enough approved questions, five forms are built and then published from
 * the exam list like any other exam.
 */
export function PlacementPrepPanel({ onBuilt }: { onBuilt: () => void }) {
  const pool = useApiQuery((signal) => getPlacementPool(signal), [])
  const [pending, setPending] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const [error, setError] = useState<string>()
  const [confirming, setConfirming] = useState(false)
  const busy = useRef(false)

  async function run(key: string, action: () => Promise<string>) {
    if (busy.current) return
    busy.current = true
    setPending(key)
    setNotice(undefined)
    setError(undefined)
    try {
      setNotice(await action())
      pool.reload()
    } catch (caught) {
      setError(failureText(caught))
    } finally {
      busy.current = false
      setPending(undefined)
    }
  }

  const levels = pool.data?.levels ?? []
  const forms = pool.data?.forms ?? []
  const ready = levels.length === 3 && levels.every((level) => level.approved >= level.needed)
  return (
    <section
      aria-labelledby="placement-prep-title"
      className="mb-6 space-y-4 rounded-md border border-line bg-surface p-4 sm:p-6"
    >
      <div className="space-y-1">
        <h2 id="placement-prep-title" className="text-h3">
          Bài kiểm tra trình độ (HSK 1–3)
        </h2>
        <p className="text-body-sm text-ink-secondary">
          Nạp câu bằng tệp CSV mẫu &ldquo;Câu hỏi&rdquo;, mã câu bắt đầu bằng <code>PLC1-</code>,{' '}
          <code>PLC2-</code> hoặc <code>PLC3-</code> theo cấp và để trống cột <code>origin</code>. Khi mỗi
          cấp đủ 50 câu, tạo 5 đề rồi xuất bản từng đề trong danh sách bên dưới.
        </p>
      </div>
      {pool.state === 'error' ? (
        <div role="alert" className="space-y-2">
          <p>Không tải được số câu của bài kiểm tra trình độ.</p>
          <Button variant="outline" onClick={pool.reload}>
            Thử lại
          </Button>
        </div>
      ) : null}
      {levels.length ? (
        <ul className="grid gap-3 sm:grid-cols-3">
          {levels.map((level) => (
            <li key={level.level} className="space-y-2 rounded-md border border-line p-3">
              <p className="font-semibold">HSK {level.level}</p>
              <p className="text-body-sm">
                {level.approved}/{level.needed} câu đã duyệt
              </p>
            </li>
          ))}
        </ul>
      ) : null}
      {forms.length ? (
        <ul className="flex flex-wrap gap-2" aria-label="Đề kiểm tra trình độ">
          {forms.map((form) => (
            <li key={form.code}>
              <StatusBadge tone={form.status === 'PUBLISHED' ? 'success' : 'info'}>
                {form.code} · {statusLabels[form.status] ?? form.status} · {form.questions} câu
              </StatusBadge>
            </li>
          ))}
        </ul>
      ) : null}
      <div className="flex flex-wrap items-center gap-3">
        <Button variant="outline" to="/content/imports">
          Nạp câu bằng CSV
        </Button>
        <Button disabled={!!pending || !ready} onClick={() => setConfirming(true)}>
          Tạo 5 đề xếp trình độ
        </Button>
        {!ready && levels.length ? (
          <p className="text-body-sm text-ink-secondary">Cần đủ 50 câu đã duyệt cho mỗi cấp.</p>
        ) : null}
      </div>
      {notice ? <p role="status">{notice}</p> : null}
      {error ? <p role="alert">{error}</p> : null}
      <ConfirmPanel
        open={confirming}
        pending={pending === 'build'}
        title="Tạo 5 đề xếp trình độ?"
        confirmLabel="Tạo đề"
        message="Đề nháp được tạo mới hoặc làm lại từ câu đã duyệt; đề đã xuất bản giữ nguyên."
        onCancel={() => setConfirming(false)}
        onConfirm={() => {
          setConfirming(false)
          void run('build', async () => {
            const result = await buildPlacementForms()
            onBuilt()
            return result.built.length
              ? `Đã tạo ${result.built.join(', ')}. Mở từng đề để kiểm tra rồi xuất bản.`
              : 'Các đề đều đã xuất bản nên được giữ nguyên.'
          })
        }}
      />
    </section>
  )
}
