import { AlertTriangle } from 'lucide-react'

import { levelText, rubricByCode, SEVERITY_LABELS } from '../rubrics'
import { criterionLabelsFor, type GradingMode, type GradingResult } from '../types'

/** Per-criterion marks of a grade: the rubric of the competency tree, or the four legacy 0–25 marks. */
export function GradingScoreBreakdown({
  mode,
  result,
}: {
  mode?: GradingMode
  result: GradingResult
}) {
  if (result.rubric && result.scores) {
    const rubric = rubricByCode(result.rubric)
    const scores = result.scores
    return (
      <div className="space-y-4">
        <p className="text-caption font-semibold uppercase tracking-wide text-ink-secondary">
          Chấm theo {rubric.label} · thang 0–{rubric.max}
        </p>
        <dl className="space-y-2">
          {rubric.criteria.map((criterion) => {
            const score = scores[criterion.code] ?? 0
            return (
              <div
                className="min-w-0 rounded-md border border-brand-100 bg-brand-50 p-3.5"
                key={criterion.code}
              >
                <div className="flex items-baseline justify-between gap-3">
                  <dt className="text-label text-ink">
                    {criterion.code} · {criterion.label}
                  </dt>
                  <dd className="text-h3 font-semibold tabular-nums text-ink">
                    {score}/{rubric.max}
                  </dd>
                </div>
                <div
                  aria-hidden="true"
                  className="mt-2 h-1.5 overflow-hidden rounded-pill bg-surface"
                >
                  <div
                    className="h-full rounded-pill bg-brand-600"
                    style={{ width: `${(score / rubric.max) * 100}%` }}
                  />
                </div>
                {levelText(criterion, score) ? (
                  <p className="mt-2 text-body-sm text-ink-secondary">
                    {levelText(criterion, score)}
                  </p>
                ) : null}
              </div>
            )
          })}
        </dl>
        {result.errors && result.errors.length > 0 ? (
          <div className="space-y-2">
            <h3 className="text-h3 text-ink">Lỗi cần sửa</h3>
            <ul className="space-y-2">
              {result.errors.map((error) => (
                <li
                  className="min-w-0 rounded-md border border-warning bg-warning-bg p-3.5"
                  key={error.code}
                >
                  <p className="flex flex-wrap items-center gap-2 text-label text-ink">
                    <AlertTriangle aria-hidden="true" className="size-4 shrink-0 text-warning" />
                    <span className="font-mono text-caption">{error.code}</span>
                    {error.nameVi}
                    <span className="rounded-pill bg-surface px-2 py-0.5 text-caption text-ink-secondary">
                      Mức {SEVERITY_LABELS[error.severity] ?? error.severity}
                    </span>
                  </p>
                  {error.fixHint ? (
                    <p className="mt-1 text-body-sm text-ink-secondary">
                      Cách sửa: {error.fixHint}
                    </p>
                  ) : null}
                </li>
              ))}
            </ul>
          </div>
        ) : null}
      </div>
    )
  }

  const criteria = result.criteria
  if (!criteria) return null
  const labels = criterionLabelsFor(mode)
  return (
    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
      {(['grammar', 'vocabulary', 'coherence', 'writing'] as const).map((key) => (
        <div className="rounded-md border border-brand-100 bg-brand-50 p-3.5" key={key}>
          <dt className="text-caption text-ink-secondary">{labels[key]}</dt>
          <dd className="mt-1 text-h3 font-semibold tabular-nums text-ink">{criteria[key]}/25</dd>
        </div>
      ))}
    </dl>
  )
}
