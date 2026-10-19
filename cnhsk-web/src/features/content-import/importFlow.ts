import { toApiError } from '@/shared/api'

import type { ImportDataType } from './types'

export const IMPORT_MAX_FILE_SIZE = 2 * 1024 * 1024

export interface ImportSelectionErrors {
  dataType?: string
  file?: string
}

export function validateImportSelection(
  dataType: ImportDataType | '',
  file: File | undefined,
): ImportSelectionErrors {
  const errors: ImportSelectionErrors = {}
  if (!dataType) errors.dataType = 'Hãy chọn loại dữ liệu.'
  if (!file) {
    errors.file = 'Hãy chọn file CSV cần kiểm tra.'
  } else if (!file.name.toLocaleLowerCase('vi').endsWith('.csv')) {
    errors.file = 'Chỉ chấp nhận file có đuôi .csv.'
  } else if (file.size > IMPORT_MAX_FILE_SIZE) {
    errors.file = 'File vượt quá giới hạn 2 MB.'
  }
  return errors
}

export interface ImportMappedError {
  message: string
  requestId?: string
}

export function mapImportUploadError(error: unknown): ImportMappedError {
  const apiError = toApiError(error)
  const messages: Record<string, string> = {
    FILE_TOO_LARGE: 'File vượt quá giới hạn 2 MB.',
    VALIDATION_FAILED: 'Loại dữ liệu hoặc file chưa hợp lệ.',
    UNSUPPORTED_FILE_FORMAT: 'File không đúng định dạng CSV được hỗ trợ.',
    ENCODING_ERROR: 'File không phải UTF-8 hoặc chữ Hán bị hỏng.',
    TEMPLATE_MISMATCH: 'Các cột trong file không khớp mẫu đã chọn.',
    EMPTY_FILE: 'File không có dòng dữ liệu để kiểm tra.',
    TOO_MANY_ROWS: 'File vượt quá giới hạn 5.000 dòng dữ liệu.',
  }
  return {
    message: messages[apiError.errorCode] ?? apiError.message,
    requestId: apiError.requestId,
  }
}

export type ConfirmErrorKind = 'retryable' | 'rerun' | 'terminal'

export interface ConfirmImportError extends ImportMappedError {
  kind: ConfirmErrorKind
}

export function mapConfirmImportError(error: unknown): ConfirmImportError {
  const apiError = toApiError(error)
  const base = { requestId: apiError.requestId }

  switch (apiError.errorCode) {
    case 'CONCURRENT_IMPORT':
      return {
        ...base,
        kind: 'retryable',
        message: 'Đang có lần nhập khác chạy, vui lòng thử lại sau ít phút.',
      }
    case 'RERUN_CONFIRMATION_REQUIRED':
      return {
        ...base,
        kind: 'rerun',
        message: 'File này đã được nhập trước đó. Hãy xác nhận nhập lại rồi thử lại.',
      }
    case 'IMPORT_RUN_NOT_FOUND':
      return {
        ...base,
        kind: 'terminal',
        message: 'Không tìm thấy lần nhập dữ liệu. Hãy tải file lên lại.',
      }
    case 'IMPORT_NOT_PENDING':
      return {
        ...base,
        kind: 'terminal',
        message: 'Lần nhập này không còn chờ xác nhận. Hãy tải file lên lại.',
      }
    case 'IMPORT_PREVIEW_EXPIRED':
      return {
        ...base,
        kind: 'terminal',
        message: 'Kết quả kiểm tra đã hết hạn, hãy tải file lên lại.',
      }
    case 'NO_VALID_ROWS':
      return {
        ...base,
        kind: 'terminal',
        message: 'Không có dòng hợp lệ để nhập. Hãy tải file lên lại.',
      }
    default:
      return { ...base, kind: 'retryable', message: apiError.message }
  }
}
