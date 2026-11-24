import { Plus, X } from 'lucide-react'
import { useState } from 'react'

import { SEVERITY_LABELS } from '@/features/grading/rubrics'
import { Button } from '@/shared/ui'

import { MAX_ERROR_CODES } from '../gradingRules'
import type { ErrorCodeOption } from '../types'

const DOMAINS = [
  { code: '', label: 'Mọi miền' },
  { code: 'NA', label: 'Ngữ âm' },
  { code: 'CH', label: 'Chữ Hán' },
  { code: 'TV', label: 'Từ vựng' },
  { code: 'NP', label: 'Ngữ pháp' },
  { code: 'NL', label: 'Giao tiếp' },
] as const

function domainOf(code: string): string {
  return code.split('-')[1] ?? ''
}

/**
 * Lets the teacher confirm appendix-D error codes. Moderate and serious codes count against their node;
 * minor ones only reach the learner as feedback, which the severity tag makes visible before choosing.
 */
export function ErrorCodePicker({
  codes,
  error,
  onChange,
  selected,
}: {
  codes: readonly ErrorCodeOption[]
  error?: string
  onChange: (codes: string[]) => void
  selected: readonly string[]
}) {
  const [domain, setDomain] = useState('')
  const [choice, setChoice] = useState('')
  const available = codes.filter(
    (option) => !selected.includes(option.code) && (!domain || domainOf(option.code) === domain),
  )
  const byCode = new Map(codes.map((option) => [option.code, option]))
  const full = selected.length >= MAX_ERROR_CODES

  function add() {
    if (!choice || selected.includes(choice) || full) return
    onChange([...selected, choice])
    setChoice('')
  }

  return (
    <section
      aria-label="Gắn mã lỗi"
      className="min-w-0 space-y-3 rounded-md border border-warning bg-warning-bg p-4 sm:p-5"
    >
      <div className="space-y-1">
        <h2 className="text-h3 text-ink">
          Mã lỗi ({selected.length}/{MAX_ERROR_CODES})
        </h2>
        <p className="text-body-sm text-ink-secondary">
          Lỗi mức Vừa và Nặng tính vào nút kiến thức của học viên; mức Nhẹ chỉ là góp ý.
        </p>
      </div>
      {codes.length === 0 ? (
        <p className="text-body-sm text-ink-secondary" role="status">
          Chưa có danh mục mã lỗi. Nội dung viên nạp file mã lỗi ở màn Nhập dữ liệu.
        </p>
      ) : (
        <div className="flex min-w-0 flex-wrap items-end gap-2">
          <label className="flex min-w-0 flex-col gap-1 text-caption text-ink-secondary">
            Miền
            <select
              className="min-h-10 rounded-md border border-line bg-surface px-3 text-body-sm text-ink"
              onChange={(event) => {
                setDomain(event.target.value)
                setChoice('')
              }}
              value={domain}
            >
              {DOMAINS.map((option) => (
                <option key={option.code} value={option.code}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <label className="flex min-w-0 flex-1 flex-col gap-1 text-caption text-ink-secondary">
            Mã lỗi
            <select
              className="min-h-10 w-full min-w-0 rounded-md border border-line bg-surface px-3 text-body-sm text-ink"
              onChange={(event) => setChoice(event.target.value)}
              value={choice}
            >
              <option value="">Chọn mã lỗi…</option>
              {available.map((option) => (
                <option key={option.code} value={option.code}>
                  {option.code} · {option.nameVi} (
                  {SEVERITY_LABELS[option.severity] ?? option.severity})
                </option>
              ))}
            </select>
          </label>
          <Button
            disabled={!choice || full}
            onClick={add}
            size="sm"
            type="button"
            variant="outline"
          >
            <Plus aria-hidden="true" className="mr-1 inline-block size-4 align-middle" />
            Gắn mã
          </Button>
        </div>
      )}
      {selected.length > 0 ? (
        <ul className="flex flex-wrap gap-2">
          {selected.map((code) => {
            const option = byCode.get(code)
            return (
              <li
                className="inline-flex max-w-full items-center gap-2 rounded-pill border border-line bg-surface py-1 pl-3 pr-1 text-body-sm text-ink"
                key={code}
                title={option?.fixHint ?? undefined}
              >
                <span className="font-mono text-caption">{code}</span>
                <span className="min-w-0 truncate">{option?.nameVi ?? ''}</span>
                <button
                  aria-label={`Bỏ mã ${code}`}
                  className="flex size-7 items-center justify-center rounded-pill text-ink-secondary hover:bg-canvas hover:text-danger"
                  onClick={() => onChange(selected.filter((value) => value !== code))}
                  type="button"
                >
                  <X aria-hidden="true" className="size-4" />
                </button>
              </li>
            )
          })}
        </ul>
      ) : null}
      {error ? (
        <p className="text-body-sm text-danger" role="alert">
          {error}
        </p>
      ) : null}
    </section>
  )
}
