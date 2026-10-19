import { apiRequest } from '@/shared/api'

import type {
  ConfirmImportRequest,
  ImportDataType,
  ImportPreview,
  ImportRun,
  ImportRunPage,
  ImportTemplate,
} from '../types'

export function getImportTemplates(signal?: AbortSignal): Promise<ImportTemplate[]> {
  return apiRequest<ImportTemplate[]>('/api/admin/imports/templates', { signal })
}

/** FR-035: file mẫu CSV; `fileName` lấy từ Content-Disposition nếu server gửi. */
export function getImportTemplateFile(dataType: ImportDataType) {
  return apiRequest(`/api/admin/imports/templates/${dataType}.csv`, { responseType: 'blob' })
}

export function uploadImportFile(dataType: ImportDataType, file: File): Promise<ImportPreview> {
  const body = new FormData()
  body.set('dataType', dataType)
  body.set('file', file)

  return apiRequest<ImportPreview>('/api/admin/imports', { body, method: 'POST' })
}

export function confirmImportRun(runId: number, body: ConfirmImportRequest): Promise<ImportRun> {
  return apiRequest<ImportRun>(`/api/admin/imports/${runId}/confirm`, {
    body,
    method: 'POST',
  })
}

export function getImportRuns(page: number, signal?: AbortSignal): Promise<ImportRunPage> {
  return apiRequest<ImportRunPage>(`/api/admin/imports?page=${page}&size=10`, { signal })
}
