import { apiRequest } from '@/shared/api'

import type { ReportReceived, ReportWrite } from '../types'

export function reportContent(body: ReportWrite): Promise<ReportReceived> {
  return apiRequest<ReportReceived>('/api/community/reports', { body, method: 'POST' })
}
