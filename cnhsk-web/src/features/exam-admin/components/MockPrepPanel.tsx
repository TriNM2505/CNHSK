import { useRef, useState } from 'react'

import { toApiError, useApiQuery } from '@/shared/api'
import { Button, ConfirmPanel, StatusBadge } from '@/shared/ui'

import {
  buildMockPapers,
  getMockPool,
  getWordImages,
  removeWordImage,
  uploadWordImage,
  type MockPartCount,
} from '../api/mockPrep'

const LEVELS = [1, 2, 3] as const

const skillLabels: Record<string, string> = { LISTENING: 'Nghe', READING: 'Đọc', WRITING: 'Viết' }
const typeLabels: Record<string, string> = {
  TRUE_FALSE: 'Đúng/sai',
  IMAGE_MATCH: 'Chọn hình',
  MULTIPLE_CHOICE: 'Trắc nghiệm',
  SENTENCE_MATCH: 'Ghép câu',
  SENTENCE_ORDER: 'Sắp xếp câu',
  FILL_BLANK: 'Điền chữ Hán',
}
const statusLabels: Record<string, string> = { DRAFT: 'Nháp', PUBLISHED: 'Đã xuất bản', ARCHIVED: 'Đã lưu trữ' }

function failureText(caught: unknown): string {
  const error = toApiError(caught)
  if (error.errorCode === 'MEDIA_NOT_CONFIGURED') return 'Chưa bật tải ảnh lên Cloudinary.'
  if (error.errorCode === 'MOCK_BANK_INSUFFICIENT') {
    const details = Array.isArray(error.details) ? error.details : []
    const missing = details.flatMap((detail: unknown) => {
      if (!detail || typeof detail !== 'object') return []
      const { part, available, needed } = detail as Record<string, unknown>
      return typeof part === 'string' ? [`phần ${part} mới có ${String(available)}/${String(needed)} câu`] : []
    })
    return missing.length
      ? `Chưa đủ câu đã nạp: ${missing.join('; ')}.`
      : 'Chưa đủ câu đã nạp cho mọi phần.'
  }
  return error.message || 'Chưa thực hiện được. Vui lòng thử lại.'
}

function partLabel(part: MockPartCount) {
  const picture = part.picture === 'NONE' ? '' : part.picture === 'STEM' ? ' · có hình câu' : ' · phương án là hình'
  return `${skillLabels[part.skill] ?? part.skill} – ${typeLabels[part.qtype] ?? part.qtype}${picture}`
}

function WordImages({ level }: { level: number }) {
  const words = useApiQuery((signal) => getWordImages(level, signal), [level])
  const [pending, setPending] = useState<number>()
  const [copied, setCopied] = useState<number>()
  const [error, setError] = useState<string>()
  const list = words.data ?? []
  const pictured = list.filter((word) => word.imageUrl).length

  async function change(lexemeId: number, action: () => Promise<unknown>) {
    setPending(lexemeId)
    setError(undefined)
    try {
      await action()
      words.reload()
    } catch (caught) {
      setError(failureText(caught))
    } finally {
      setPending(undefined)
    }
  }

  return (
    <details className="rounded-md border border-line p-3">
      <summary className="min-h-touch cursor-pointer font-semibold">
        Ảnh thật cho từ vựng HSK {level} ({pictured}/{list.length} từ có ảnh)
      </summary>
      <p className="mt-2 text-body-sm text-ink-secondary">
        Không bắt buộc: câu có hình dùng emoji được. Từ nào khó vẽ bằng emoji thì tải ảnh thật (tự chụp hoặc miễn phí
        bản quyền như Pixabay, Unsplash, Openclipart), bấm <strong>Chép link</strong> rồi dán vào cột{' '}
        <code>image_url</code> hoặc <code>option_images</code> của file nạp. Mỗi từ một ảnh, dùng lại cho mọi đề.
      </p>
      {error ? <p role="alert" className="mt-2 text-danger">{error}</p> : null}
      <ul className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
        {list.map((word) => (
          <li key={word.lexemeId} className="flex items-center gap-3 rounded-md border border-line p-2">
            {word.imageUrl ? (
              <img alt={`Ảnh của ${word.text}`} className="size-14 shrink-0 rounded-sm object-cover" src={word.imageUrl} />
            ) : (
              <span className="flex size-14 shrink-0 items-center justify-center rounded-sm bg-brand-50 text-caption text-ink-muted">
                Chưa có
              </span>
            )}
            <span className="min-w-0 flex-1">
              <span className="block font-hanzi text-lg" lang="zh-CN">
                {word.text}
              </span>
              <span className="block truncate text-caption text-ink-secondary">
                {word.pinyin} · {word.meaningVi}
              </span>
            </span>
            <label className="cursor-pointer text-body-sm font-semibold text-brand-700">
              {pending === word.lexemeId ? 'Đang tải…' : word.imageUrl ? 'Đổi' : 'Tải ảnh'}
              <input
                accept="image/*"
                aria-label={`Tải ảnh cho ${word.text}`}
                className="sr-only"
                disabled={pending !== undefined}
                type="file"
                onChange={(event) => {
                  const file = event.target.files?.[0]
                  event.target.value = ''
                  if (file) void change(word.lexemeId, () => uploadWordImage(word.lexemeId, file))
                }}
              />
            </label>
            {word.imageUrl ? (
              <Button
                size="sm"
                variant="ghost"
                aria-label={`Chép link ảnh của ${word.text}`}
                onClick={() => {
                  const url = word.imageUrl ?? ''
                  navigator.clipboard.writeText(url).then(
                    () => setCopied(word.lexemeId),
                    () => setError(`Không chép được, link ảnh: ${url}`),
                  )
                }}
              >
                {copied === word.lexemeId ? 'Đã chép' : 'Chép link'}
              </Button>
            ) : null}
            {word.imageUrl ? (
              <Button
                size="sm"
                variant="ghost"
                disabled={pending !== undefined}
                aria-label={`Gỡ ảnh của ${word.text}`}
                onClick={() => void change(word.lexemeId, () => removeWordImage(word.lexemeId))}
              >
                Gỡ
              </Button>
            ) : null}
          </li>
        ))}
      </ul>
    </details>
  )
}

/**
 * Content editors prepare the HSK-format mock papers: questions are imported by hand part by part, word photos are
 * uploaded where an emoji will not do, then three papers per level are built and published from the exam list.
 */
export function MockPrepPanel({ onBuilt }: { onBuilt: () => void }) {
  const [level, setLevel] = useState<number>(1)
  const pool = useApiQuery((signal) => getMockPool(level, signal), [level])
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

  const parts = pool.data?.parts ?? []
  const papers = pool.data?.papers ?? []
  const ready = parts.length > 0 && parts.every((part) => part.approved >= part.needed)
  return (
    <section aria-labelledby="mock-prep-title" className="mb-6 space-y-4 rounded-md border border-line bg-surface p-4 sm:p-6">
      <div className="space-y-1">
        <h2 id="mock-prep-title" className="text-h3">
          Đề thi thử theo format HSK 2.0
        </h2>
        <p className="text-body-sm text-ink-secondary">
          Mỗi cấp 3 đề, đủ phần Nghe – Đọc (HSK 3 thêm Viết), máy chấm hết. Nạp câu từng phần bằng mẫu{' '}
          <strong>Câu hỏi</strong> ở trang Nạp dữ liệu, đặt mã theo phần như bên dưới. Câu nghe: lời nói ở{' '}
          <code>stem</code>, máy đọc, không hiện chữ. Hình: emoji ngay trong chữ, hoặc link ảnh thật. Đủ câu thì tạo
          đề rồi xuất bản từ danh sách bên dưới.
        </p>
      </div>
      <div className="flex flex-wrap gap-2" role="group" aria-label="Cấp HSK">
        {LEVELS.map((value) => (
          <Button
            key={value}
            aria-pressed={level === value}
            variant={level === value ? 'primary' : 'outline'}
            onClick={() => {
              setLevel(value)
              setNotice(undefined)
              setError(undefined)
            }}
          >
            HSK {value}
          </Button>
        ))}
      </div>
      {pool.state === 'error' ? (
        <div role="alert" className="space-y-2">
          <p>Không tải được số câu của đề thi thử.</p>
          <Button variant="outline" onClick={pool.reload}>
            Thử lại
          </Button>
        </div>
      ) : null}
      {parts.length ? (
        <ul className="grid gap-3 sm:grid-cols-2" aria-label={`Các phần đề HSK ${level}`}>
          {parts.map((part) => (
            <li key={part.part} className="space-y-2 rounded-md border border-line p-3">
              <p className="font-semibold">
                {part.part} · {partLabel(part)}
              </p>
              <p className="text-body-sm">
                {part.approved}/{part.needed} câu đã nạp
                {part.pending ? ` · ${part.pending} câu đang chờ duyệt` : ''}
              </p>
              <p className="text-caption text-ink-secondary">
                Mã <code>MCK{level}-{part.part}-…</code> · qtype <code>{part.qtype}</code> · skill{' '}
                <code>{part.skill}</code>
                {part.options ? ` · ${part.options} phương án` : ''}. {part.guide}
              </p>
            </li>
          ))}
        </ul>
      ) : null}
      <WordImages level={level} />
      {papers.length ? (
        <ul className="flex flex-wrap gap-2" aria-label={`Đề thi thử HSK ${level}`}>
          {papers.map((paper) => (
            <li key={paper.code}>
              <StatusBadge tone={paper.status === 'PUBLISHED' ? 'success' : 'info'}>
                {paper.code} · {statusLabels[paper.status] ?? paper.status} · {paper.questions} câu
              </StatusBadge>
            </li>
          ))}
        </ul>
      ) : null}
      <div className="flex flex-wrap items-center gap-3">
        <Button disabled={!!pending || !ready} onClick={() => setConfirming(true)}>
          Tạo 3 đề HSK {level}
        </Button>
        {!ready && parts.length ? (
          <p className="text-body-sm text-ink-secondary">Cần đủ câu đã duyệt cho mọi phần.</p>
        ) : null}
      </div>
      {notice ? <p role="status">{notice}</p> : null}
      {error ? <p role="alert">{error}</p> : null}
      <ConfirmPanel
        open={confirming}
        pending={pending === 'build'}
        title={`Tạo 3 đề thi thử HSK ${level}?`}
        confirmLabel="Tạo đề"
        message="Đề nháp được tạo mới hoặc làm lại từ câu đã duyệt; đề đã xuất bản giữ nguyên."
        onCancel={() => setConfirming(false)}
        onConfirm={() => {
          setConfirming(false)
          void run('build', async () => {
            const result = await buildMockPapers(level)
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
