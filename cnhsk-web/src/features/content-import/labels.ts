import type { ImportDataType, ImportRowAction, ImportRunStatus } from './types'

export const IMPORT_DATA_TYPE_LABELS: Record<ImportDataType, string> = {
  LEXEME: 'Chữ và từ vựng',
  TOPIC: 'Chủ đề từ vựng',
  TOPIC_ITEM: 'Từ trong chủ đề',
  GRAMMAR: 'Điểm ngữ pháp',
  GRAMMAR_EXERCISE: 'Bài tập ngữ pháp',
  PRON_STAGE: 'Tầng phát âm',
  PRON_UNIT: 'Bài nghe phát âm',
}

export const IMPORT_DATA_TYPES = Object.keys(IMPORT_DATA_TYPE_LABELS) as ImportDataType[]

export const IMPORT_ROW_ACTION_LABELS: Record<ImportRowAction, string> = {
  CREATE: 'Tạo mới',
  UPDATE: 'Cập nhật',
  UNCHANGED: 'Không đổi',
}

export const IMPORT_RUN_STATUS_LABELS: Record<ImportRunStatus, string> = {
  PENDING: 'Chờ xác nhận',
  RUNNING: 'Đang nhập',
  COMPLETED: 'Hoàn thành',
  FAILED: 'Thất bại',
  EXPIRED: 'Hết hạn',
}

export function isImportDataType(value: string | null | undefined): value is ImportDataType {
  return Boolean(value && IMPORT_DATA_TYPES.includes(value as ImportDataType))
}
