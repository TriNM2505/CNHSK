import { describe, expect, it } from 'vitest'

import { ApiError } from '@/shared/api'

import { mapImportUploadError, validateImportSelection } from './importFlow'

describe('import selection and template errors', () => {
  it.each(['data.csv', 'data.CSV', 'data.Csv'])('FR036: accepts %s', (name) => {
    expect(validateImportSelection('TOPIC', new File(['data'], name))).toEqual({})
  })

  it.each(['data.txt', 'file.csv.exe'])('FR036: rejects %s', (name) => {
    expect(validateImportSelection('TOPIC', new File(['data'], name)).file).toBeDefined()
  })

  it('FR006: preserves template mismatch details from the API contract', () => {
    const details = [
      { field: 'pinyin', message: 'Thiếu cột bắt buộc: pinyin' },
      { field: 'note', message: 'Cột không có trong mẫu: note' },
      { field: 'text', message: 'Tên cột bị trùng sau khi bỏ khoảng trắng: text' },
    ]
    const error = new ApiError({
      errorCode: 'TEMPLATE_MISMATCH',
      message: 'Sai mẫu',
      status: 422,
      details,
    })
    expect(mapImportUploadError(error)).toMatchObject({ details })
  })
})
