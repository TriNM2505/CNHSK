import { CircleAlert, FileText, Layers, Megaphone, Save } from 'lucide-react'
import { Button, FormField, Input, PageHeader, Select, SUPPORTED_HSK_LEVELS } from '@/shared/ui'
import { pointsTotal, statusLabels } from '../examForm'
import type { ExamDetail, ExamForm, PublicationError } from '../types'
import { ExamSectionsEditor } from './ExamSectionsEditor'
import { ExamQuestionsEditor } from './ExamQuestionsEditor'

export type ExamEditorViewProps = {
  form: ExamForm
  saved: ExamDetail | null
  dirty: boolean
  busy: boolean
  locked?: boolean
  errors: string[]
  publicationErrors: PublicationError[]
  notice?: string
  onChange: (form: ExamForm) => void
  onSave: () => void
  onPublish: () => void
  onArchive: () => void
  onClone: () => void
  onReload: () => void
  onBack: () => void
  onPicker: () => void
}

function ChineseLandscapeBannerArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-y-0 right-0 hidden h-full w-3/5 text-brand-600 sm:block"
      fill="none"
      preserveAspectRatio="xMaxYMid slice"
      viewBox="0 0 680 132"
    >
      <defs>
        <linearGradient id="cnhsk-exed-mtn-far" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
        </linearGradient>
        <linearGradient id="cnhsk-exed-mtn-mid" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.05" />
        </linearGradient>
        <linearGradient id="cnhsk-exed-hill-front" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.34" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.12" />
        </linearGradient>
      </defs>

      <circle cx="518" cy="34" fill="currentColor" opacity="0.2" r="24" />
      <rect fill="currentColor" height="5" opacity="0.16" rx="2.5" width="54" x="466" y="42" />
      <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="42" x="524" y="24" />
      <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="38" x="380" y="34" />

      <path
        d="M10 132 L110 66 L158 94 L236 22 L318 88 L382 36 L455 92 L520 58 L600 104 L680 132 Z"
        fill="url(#cnhsk-exed-mtn-far)"
      />
      <path d="M236 22 L252 88 L195 112 Z" fill="currentColor" opacity="0.08" />
      <path d="M382 36 L396 86 L348 102 Z" fill="currentColor" opacity="0.08" />

      <path
        d="M95 132 L176 74 L248 106 L312 68 L392 108 L468 74 L556 106 L680 132 Z"
        fill="url(#cnhsk-exed-mtn-mid)"
      />

      <path
        d="M280 132 C345 92, 415 74, 480 88 C538 100, 588 92, 680 112 L680 132 Z"
        fill="url(#cnhsk-exed-hill-front)"
      />
      <path
        d="M390 132 C456 104, 520 98, 585 112 C622 104, 654 106, 680 118 L680 132 Z"
        fill="currentColor"
        opacity="0.22"
      />

      <path
        d="M284 132 C328 96, 376 70, 428 72 C474 74, 514 104, 562 102 C598 100, 632 88, 676 94 L676 106 C632 100, 598 112, 562 114 C512 116, 472 86, 428 84 C378 82, 334 106, 298 132 Z"
        fill="currentColor"
        opacity="0.32"
      />
      <path
        d="M282 132 C326 94, 376 68, 428 70 C474 72, 514 102, 562 100 C598 98, 632 86, 676 92"
        opacity="0.58"
        stroke="currentColor"
        strokeDasharray="5 3.5"
        strokeWidth="4.5"
      />
      <path
        d="M294 132 C336 102, 380 78, 428 80 C474 82, 514 112, 562 110 C598 108, 632 96, 676 102"
        opacity="0.45"
        stroke="currentColor"
        strokeWidth="2"
      />

      <g opacity="0.55" transform="translate(506, 70) scale(0.58)">
        <path d="M22 2 L25 8 L19 8 Z" fill="currentColor" />
        <path d="M4 16 Q14 13 22 6 Q30 13 40 16 Q31 18 22 14 Q13 18 4 16 Z" fill="currentColor" />
        <path d="M0 25 Q12 22 22 16 Q32 22 44 25 Q33 26 22 23 Q11 26 0 25 Z" fill="currentColor" />
        <rect fill="currentColor" height="11" width="3" x="11" y="24" />
        <rect fill="currentColor" height="11" width="3" x="20.5" y="24" />
        <rect fill="currentColor" height="11" width="3" x="30" y="24" />
        <rect fill="currentColor" height="3.5" width="32" x="6" y="34" />
      </g>

      <g opacity="0.5" transform="translate(594, 78) scale(0.62)">
        <path d="M22 2 L25 8 L19 8 Z" fill="currentColor" />
        <path d="M4 16 Q14 13 22 6 Q30 13 40 16 Q31 18 22 14 Q13 18 4 16 Z" fill="currentColor" />
        <path d="M0 25 Q12 22 22 16 Q32 22 44 25 Q33 26 22 23 Q11 26 0 25 Z" fill="currentColor" />
        <rect fill="currentColor" height="11" width="3" x="11" y="24" />
        <rect fill="currentColor" height="11" width="3" x="20.5" y="24" />
        <rect fill="currentColor" height="11" width="3" x="30" y="24" />
        <rect fill="currentColor" height="3.5" width="32" x="6" y="34" />
      </g>

      <g className="text-brand-700" opacity="0.95" transform="translate(426, 26)">
        <path d="M28 0 L31 8 L25 8 Z" fill="currentColor" />
        <path
          d="M6 19 C15 17, 22 12, 28 6 C34 12, 41 17, 50 19 C42 21, 35 18, 28 15 C21 18, 14 21, 6 19 Z"
          fill="currentColor"
        />
        <rect fill="currentColor" height="6" width="22" x="17" y="17" />
        <path
          d="M0 31 C12 28, 21 23, 28 18 C35 23, 44 28, 56 31 C45 33, 36 30, 28 27 C20 30, 11 33, 0 31 Z"
          fill="currentColor"
        />
        <rect fill="currentColor" height="15" width="3" x="12" y="29" />
        <rect fill="currentColor" height="15" width="3" x="20" y="29" />
        <rect fill="currentColor" height="15" width="3" x="33" y="29" />
        <rect fill="currentColor" height="15" width="3" x="41" y="29" />
        <rect fill="currentColor" height="2.5" width="34" x="11" y="39" />
        <path d="M7 44 L49 44 L52 49 L4 49 Z" fill="currentColor" />
      </g>

      <g className="text-brand-700" opacity="0.92">
        <path d="M634 134 L600 -8" stroke="currentColor" strokeWidth="6" />
        <line stroke="white" strokeWidth="2" x1="622" x2="630" y1="96" y2="94" />
        <line stroke="white" strokeWidth="2" x1="613" x2="621" y1="58" y2="56" />
        <line stroke="white" strokeWidth="2" x1="604" x2="612" y1="20" y2="18" />

        <path d="M654 134 L634 -8" stroke="currentColor" strokeWidth="8.5" />
        <line stroke="white" strokeWidth="2.2" x1="644" x2="654" y1="102" y2="100" />
        <line stroke="white" strokeWidth="2.2" x1="639" x2="649" y1="64" y2="62" />
        <line stroke="white" strokeWidth="2.2" x1="634" x2="644" y1="26" y2="24" />

        <path d="M672 134 L662 -8" stroke="currentColor" strokeWidth="6.5" />
        <line stroke="white" strokeWidth="2" x1="664" x2="673" y1="86" y2="85" />
        <line stroke="white" strokeWidth="2" x1="661" x2="670" y1="46" y2="45" />

        <path
          d="M636 10 Q585 6 542 26"
          stroke="currentColor"
          strokeLinecap="round"
          strokeWidth="2"
        />
        <path
          d="M612 36 Q576 42 548 62"
          stroke="currentColor"
          strokeLinecap="round"
          strokeWidth="1.8"
        />

        <path d="M558 18 Q540 32 528 50 Q548 38 558 18 Z" fill="currentColor" />
        <path d="M570 14 Q558 36 550 58 Q568 40 570 14 Z" fill="currentColor" />
        <path d="M584 12 Q578 34 574 54 Q588 36 584 12 Z" fill="currentColor" />
        <path d="M554 20 Q534 20 518 28 Q540 28 554 20 Z" fill="currentColor" opacity="0.85" />

        <path d="M562 52 Q546 66 536 82 Q554 70 562 52 Z" fill="currentColor" />
        <path d="M576 46 Q566 66 560 86 Q576 68 576 46 Z" fill="currentColor" />
        <path d="M590 42 Q586 60 584 78 Q596 62 590 42 Z" fill="currentColor" />
        <path d="M556 54 Q538 56 524 64 Q544 64 556 54 Z" fill="currentColor" opacity="0.8" />

        <path d="M596 8 Q586 24 582 40 Q596 28 596 8 Z" fill="currentColor" />
        <path d="M618 62 Q606 76 600 92 Q614 80 618 62 Z" fill="currentColor" />
        <path d="M628 58 Q622 76 620 92 Q632 76 628 58 Z" fill="currentColor" />
      </g>
    </svg>
  )
}

export function ExamEditorView({
  form,
  saved,
  dirty,
  busy,
  locked,
  errors,
  publicationErrors,
  notice,
  onChange,
  onSave,
  onPublish,
  onArchive,
  onClone,
  onReload,
  onBack,
  onPicker,
}: ExamEditorViewProps) {
  const readonly = !!saved && saved.status !== 'DRAFT'
  const disabled = busy || readonly || !!locked
  const sum = pointsTotal(form)
  const level = Number(form.hskLevel)
  return (
    <main className="mx-auto w-full min-w-0 max-w-reading space-y-5 break-words px-4 py-6 pb-16 sm:px-6">
      <div className="relative overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6">
        <PageHeader
          className="relative z-nav mb-0"
          title={
            <span className="inline-flex items-center gap-3">
              <span aria-hidden="true" className="h-7 w-1.5 rounded-pill bg-brand-600" />
              <span>{saved ? `Đề thi · ${saved.code}` : 'Tạo đề nháp'}</span>
            </span>
          }
          subtitle={
            saved
              ? `${statusLabels[saved.status]}${dirty ? ' · Có thay đổi chưa lưu' : ' · Bản đã lưu'}`
              : 'Lưu nháp trước khi công bố.'
          }
          actions={
            <Button variant="outline" disabled={busy} onClick={onBack}>
              Về danh sách đề
            </Button>
          }
        />
        <ChineseLandscapeBannerArt />
      </div>
      {notice && (
        <p role="status" className="rounded-md bg-brand-50 p-4">
          {notice}
        </p>
      )}
      {readonly && (
        <p className="rounded-md border border-brand-100 bg-brand-50 p-4">
          Đề {statusLabels[saved.status].toLowerCase()} chỉ đọc cấu trúc. Nhân bản để tạo bản nháp
          mới.
        </p>
      )}
      {locked && (
        <div role="alert" className="space-y-3 rounded-md border border-danger bg-danger-bg p-4">
          <p>
            Dữ liệu cần được đọc lại trước khi tiếp tục ghi. Bản nháp đang được giữ trên màn hình.
          </p>
          <Button variant="outline" disabled={busy} onClick={onReload}>
            Tải lại đề
          </Button>
        </div>
      )}
      {!!errors.length && (
        <ul
          role="alert"
          className="list-inside list-disc rounded-md border border-danger bg-danger-bg p-4"
        >
          {errors.map((error, index) => (
            <li key={index}>{error}</li>
          ))}
        </ul>
      )}
      <section
        className="min-w-0 rounded-md border border-brand-100 bg-brand-50/30 p-5"
        aria-label="Thông tin đề"
      >
        <h2 className="mb-4 flex items-center gap-2 text-h3 font-bold">
          <FileText aria-hidden="true" className="size-5 text-brand-700" />
          Thông tin đề
        </h2>
        <div className="grid min-w-0 gap-x-6 sm:grid-cols-2">
          <FormField htmlFor="exam-code" label="Mã đề" required>
            <Input
              disabled={disabled}
              value={form.code}
              placeholder="Nhập mã đề…"
              onChange={(e) => onChange({ ...form, code: e.target.value })}
            />
          </FormField>
          <FormField htmlFor="exam-title" label="Tên đề" required>
            <Input
              disabled={disabled}
              value={form.title}
              placeholder="Nhập tên đề…"
              onChange={(e) => onChange({ ...form, title: e.target.value })}
            />
          </FormField>
          <FormField htmlFor="exam-level" label="Cấp HSK" required>
            <Select
              disabled={disabled}
              value={form.hskLevel}
              onChange={(e) => onChange({ ...form, hskLevel: e.target.value })}
            >
              <option value="">Chọn cấp</option>
              {level > 3 && <option value={level}>HSK {level} (dữ liệu hiện có)</option>}
              {SUPPORTED_HSK_LEVELS.map((value) => (
                <option key={value} value={value}>
                  HSK {value}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField
            htmlFor="exam-score"
            label="Tổng điểm khai báo"
            required
            hint="Từ 1 đến 1.000 điểm. Có thể lưu nháp khi tổng điểm câu hỏi chưa khớp."
          >
            <Input
              disabled={disabled}
              type="number"
              min={1}
              max={1000}
              step={1}
              value={form.totalScore}
              placeholder="Ví dụ: 100"
              onChange={(e) => onChange({ ...form, totalScore: e.target.value })}
            />
          </FormField>
        </div>
        <p className="mb-3 flex items-center gap-2 rounded-sm bg-brand-50 p-3 text-body-sm">
          <CircleAlert aria-hidden="true" className="size-4 shrink-0 text-brand-700" />
          Các trường có dấu * là bắt buộc.
        </p>
        <p
          role="status"
          className={
            sum !== Number(form.totalScore)
              ? 'rounded-md border border-warning bg-warning-bg p-3'
              : 'rounded-md bg-brand-50 p-3'
          }
        >
          Tổng điểm câu hỏi: <strong>{sum}</strong> / {form.totalScore || 'chưa khai báo'}
          {sum !== Number(form.totalScore) &&
            ' · Chưa khớp tổng khai báo; máy chủ sẽ kiểm khi công bố.'}
        </p>
      </section>
      <ExamSectionsEditor form={form} disabled={disabled} onChange={onChange} />
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-md border border-brand-100 bg-brand-50/60 p-4">
        <h2 className="flex items-center gap-2 text-h3 font-bold">
          <Layers aria-hidden="true" className="size-5 text-brand-700" />
          Ngân hàng câu hỏi
        </h2>
        <Button
          variant="outline"
          className="border-brand-100 text-brand-700"
          disabled={disabled || form.questions.length >= 300}
          onClick={onPicker}
        >
          Chọn câu hỏi
        </Button>
      </div>
      <ExamQuestionsEditor
        form={form}
        disabled={disabled}
        onChange={onChange}
        onPicker={onPicker}
      />
      <section
        aria-label="Lỗi chặn công bố"
        className="space-y-3 rounded-md border border-brand-100 bg-brand-50/30 p-5"
      >
        <h2 className="flex items-center gap-2 text-h3 font-bold">
          <CircleAlert aria-hidden="true" className="size-5 text-brand-700" />
          Lỗi chặn công bố
        </h2>
        {dirty && (
          <p className="my-2 text-body-sm text-ink-secondary">
            Các lỗi dưới đây thuộc bản đã lưu. Lưu nháp để cập nhật kiểm tra.
          </p>
        )}
        {publicationErrors.length ? (
          <ul className="mt-3 space-y-2">
            {publicationErrors.map((error, index) => (
              <li key={index} className="break-words rounded-sm bg-warning-bg p-3">
                <strong>{error.code}</strong>: {error.message}
                {error.sectionIndex !== null &&
                  error.sectionIndex !== undefined &&
                  ` · Phần ${error.sectionIndex + 1}`}
                {error.questionId && ` · Câu #${error.questionId}`}
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-3 text-ink-secondary">
            Chưa có lỗi công bố từ máy chủ. Máy chủ sẽ kiểm lại khi công bố.
          </p>
        )}
      </section>
      <div className="flex flex-wrap gap-3 rounded-md border border-brand-100 bg-brand-50 p-4">
        {!readonly && (
          <Button className="w-full sm:w-auto" loading={busy} disabled={disabled} onClick={onSave}>
            <span className="inline-flex items-center gap-2">
              <Save aria-hidden="true" className="size-4" />
              Lưu nháp
            </span>
          </Button>
        )}
        {saved && !readonly && (
          <Button variant="secondary" disabled={busy || dirty || !!locked} onClick={onPublish}>
            <span className="inline-flex items-center gap-2">
              <Megaphone aria-hidden="true" className="size-4" />
              Công bố
            </span>
          </Button>
        )}
        {saved && (
          <>
            <Button variant="outline" disabled={busy || !!locked} onClick={onClone}>
              Nhân bản đề
            </Button>
            <Button
              variant="danger"
              disabled={busy || !!locked || saved.status === 'ARCHIVED'}
              onClick={onArchive}
            >
              Lưu trữ đề
            </Button>
          </>
        )}
        {dirty && (
          <p className="basis-full text-body-sm text-ink-secondary">
            Lưu nháp trước khi công bố. Thay đổi hiện tại chưa được áp dụng trên máy chủ.
          </p>
        )}
      </div>
    </main>
  )
}
