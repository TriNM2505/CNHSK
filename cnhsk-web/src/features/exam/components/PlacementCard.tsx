import { Award, BarChart3, GraduationCap, RotateCcw } from 'lucide-react'

import { Button } from '@/shared/ui'

import { placementLabels } from '../placementLevel'
import type { PlacementSummary } from '../types'

/** Start, resume or retake the entrance test, with the latest result when there is one. */
export function PlacementCard({
  placement,
  pending,
  onStart,
}: {
  placement?: PlacementSummary
  pending?: boolean
  onStart: () => void
}) {
  return (
    <section
      aria-label="Kiểm tra đầu vào"
      className="min-w-0 space-y-4 rounded-lg border border-brand-100 bg-brand-50 p-5 sm:p-6"
    >
      <div className="flex items-center gap-3">
        <span className="flex size-12 shrink-0 items-center justify-center rounded-md bg-brand-100 text-brand-700">
          <GraduationCap aria-hidden="true" className="size-6" />
        </span>
        <h2 className="text-h3 text-ink">Bài kiểm tra trình độ</h2>
      </div>
      <p className="text-body-sm text-ink-secondary">
        Một bài 30 câu trộn HSK 1–3 · khoảng 15 phút · đọc hiểu, từ vựng và ngữ pháp. Có kết quả
        ngay và cập nhật trình độ của bạn.
      </p>
      {placement?.active ? (
        <Button
          className="w-full !rounded-pill"
          to={`/learn/exams/sessions/${placement.active.sessionId}`}
        >
          Tiếp tục
        </Button>
      ) : placement?.last ? (
        <>
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-brand-100 bg-surface p-4">
            <div>
              <p className="text-caption text-ink-secondary">Kết quả gần nhất</p>
              <p className="mt-0.5 text-h2 font-bold text-brand-700">
                {placementLabels[placement.last.level]}
              </p>
            </div>
            {placement.last.confidence === 'TENTATIVE' ? (
              <span className="inline-flex items-center gap-1 rounded-pill bg-warning-bg px-3 py-1 text-caption font-semibold text-warning">
                <Award aria-hidden="true" className="size-3.5" />
                Kết quả tạm
              </span>
            ) : null}
            <span className="sr-only">
              Kết quả gần nhất: {placementLabels[placement.last.level]}
              {placement.last.confidence === 'TENTATIVE' ? ' — Kết quả tạm' : ''}
            </span>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Button
              variant="outline"
              className="w-full !rounded-pill"
              to={`/learn/placement/${placement.last.runId}`}
            >
              <BarChart3
                aria-hidden="true"
                className="mr-1.5 inline-block size-4 align-middle text-brand-600"
              />
              Xem kết quả
            </Button>
            <Button className="w-full !rounded-pill" disabled={pending} onClick={onStart}>
              <RotateCcw aria-hidden="true" className="mr-1.5 inline-block size-4 align-middle" />
              Làm lại
            </Button>
          </div>
        </>
      ) : (
        <Button className="w-full !rounded-pill" disabled={pending} onClick={onStart}>
          Bắt đầu
        </Button>
      )}
    </section>
  )
}
