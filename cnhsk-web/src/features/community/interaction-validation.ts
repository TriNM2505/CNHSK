import { REPORT_REASON_LABELS } from './labels'
import type { ReportReason, ReportWrite } from './types'

export function validateText(value: string, max: number): string | undefined {
  const length = value.trim().length
  return length < 1 || length > max ? `Nhập nội dung từ 1 đến ${max.toLocaleString('vi-VN')} ký tự.` : undefined
}

export function isReportReason(value: string): value is ReportReason {
  return Object.prototype.hasOwnProperty.call(REPORT_REASON_LABELS, value)
}

export function makeReport(targetType: ReportWrite['targetType'], targetId: number, reason: string, detail: string): ReportWrite | undefined {
  if (!isReportReason(reason) || detail.trim().length > 1000 || (reason === 'OTHER' && !detail.trim())) return undefined
  return { targetType, targetId, reason, detail: detail.trim() || null }
}
