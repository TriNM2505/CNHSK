import { useId, useState } from 'react'

import type { ApiError } from '@/shared/api'
import { Button, Card, FormField, Input, Select, SUPPORTED_HSK_LEVELS, Textarea } from '@/shared/ui'

import {
  fieldErrors,
  inputOf,
  kindLabels,
  validateDraft,
  type ReferenceDraft,
} from '../referenceForm'
import type { ReferenceInput } from '../types'

type Props = {
  initial: ReferenceDraft
  editing: boolean
  pending: boolean
  error?: ApiError
  onSubmit: (input: ReferenceInput) => void
  onCancel: () => void
}

export function ReferenceEditorForm({
  initial,
  editing,
  pending,
  error,
  onSubmit,
  onCancel,
}: Props) {
  const id = useId()
  const [draft, setDraft] = useState(initial)
  const [submitted, setSubmitted] = useState(false)
  const [tag, setTag] = useState('')
  const [tagError, setTagError] = useState('')
  const [changed, setChanged] = useState(false)
  const errors = {
    ...(changed ? {} : fieldErrors(error)),
    ...(submitted ? validateDraft(draft) : {}),
  }
  const change = <K extends keyof ReferenceDraft>(key: K, value: ReferenceDraft[K]) => {
    setDraft((current) => ({ ...current, [key]: value }))
    setChanged(true)
  }
  const textField = (
    key:
      | 'title'
      | 'description'
      | 'url'
      | 'thumbnailUrl'
      | 'sortOrder'
      | 'author'
      | 'publisher'
      | 'publishedYear',
    label: string,
    multiline = false,
  ) => (
    <FormField key={key} htmlFor={`${id}-${key}`} label={label} error={errors[key]}>
      {multiline ? (
        <Textarea
          disabled={pending}
          rows={4}
          value={draft[key]}
          onChange={(event) => change(key, event.target.value)}
        />
      ) : (
        <Input
          disabled={pending}
          value={draft[key]}
          inputMode={key === 'sortOrder' || key === 'publishedYear' ? 'numeric' : undefined}
          onChange={(event) => change(key, event.target.value)}
        />
      )}
    </FormField>
  )
  return (
    <Card className="min-w-0">
      <h2 className="mb-4 text-h2">{editing ? 'Sửa tài nguyên' : 'Tạo tài nguyên'}</h2>
      <form
        aria-label={editing ? 'Sửa tài nguyên' : 'Tạo tài nguyên'}
        onSubmit={(event) => {
          event.preventDefault()
          setSubmitted(true)
          setChanged(false)
          if (!pending && !Object.keys(validateDraft(draft)).length && !tag)
            onSubmit(inputOf(draft))
          if (tag) setTagError('Bấm Thêm tag trước khi lưu, hoặc xoá ô tag đang nhập.')
        }}
      >
        <fieldset
          disabled={pending}
          className="grid min-w-0 grid-cols-1 gap-x-4 min-[641px]:grid-cols-2"
        >
          <FormField htmlFor={`${id}-kind`} label="Loại" error={errors.kind}>
            <Select
              value={draft.kind}
              onChange={(event) => change('kind', event.target.value as ReferenceDraft['kind'])}
            >
              {Object.entries(kindLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </FormField>
          {textField('title', 'Tên tài nguyên')}
          {textField('url', 'Liên kết')}
          {textField('thumbnailUrl', 'Liên kết ảnh (không bắt buộc)')}
          {(['hskMin', 'hskMax'] as const).map((key) => (
            <FormField
              key={key}
              htmlFor={`${id}-${key}`}
              label={key === 'hskMin' ? 'HSK tối thiểu' : 'HSK tối đa'}
              error={errors[key]}
            >
              <Select value={draft[key]} onChange={(event) => change(key, event.target.value)}>
                <option value="">Không giới hạn</option>
                {SUPPORTED_HSK_LEVELS.map((level) => (
                  <option key={level} value={level}>
                    HSK {level}
                  </option>
                ))}
                {initial[key] && Number(initial[key]) > 3 ? (
                  <option value={initial[key]} disabled>
                    HSK {initial[key]} (giá trị hiện có)
                  </option>
                ) : null}
              </Select>
            </FormField>
          ))}
          {textField('sortOrder', 'Thứ tự hiển thị')}
          <div className="min-w-0">
            <FormField htmlFor={`${id}-tag`} label="Tag mới" error={tagError || errors.tags}>
              <Input
                value={tag}
                onChange={(event) => {
                  setTag(event.target.value)
                  setTagError('')
                }}
              />
            </FormField>
            <Button
              size="sm"
              variant="outline"
              type="button"
              disabled={pending}
              onClick={() => {
                if (!tag || [...tag].length > 30 || draft.tags.length >= 10) {
                  setTagError('Tag từ 1–30 ký tự; tối đa 10 tag.')
                  return
                }
                change('tags', [...draft.tags, tag])
                setTag('')
                setTagError('')
              }}
            >
              Thêm tag
            </Button>
            <ul className="my-3 flex flex-wrap gap-2">
              {draft.tags.map((value, index) => (
                <li key={`${index}-${value}`} className="max-w-full break-words text-body-sm">
                  <span>{value}</span>{' '}
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    aria-label={`Bỏ tag ${value}`}
                    onClick={() =>
                      change(
                        'tags',
                        draft.tags.filter((_, position) => position !== index),
                      )
                    }
                  >
                    Bỏ
                  </Button>
                </li>
              ))}
            </ul>
          </div>
          <div className="min-w-0 min-[641px]:col-span-2">
            {textField('description', 'Mô tả (không bắt buộc)', true)}
          </div>
          {draft.kind === 'BOOK' ? (
            <>
              {textField('author', 'Tác giả')}
              {textField('publisher', 'Nhà xuất bản')}
              {textField('publishedYear', 'Năm xuất bản')}
              <FormField
                htmlFor={`${id}-affiliate`}
                label="Link tiếp thị liên kết"
                error={errors.isAffiliate}
              >
                <input
                  type="checkbox"
                  checked={draft.isAffiliate}
                  onChange={(event) => change('isAffiliate', event.target.checked)}
                />
              </FormField>
            </>
          ) : null}
        </fieldset>
        {error ? (
          <p role="alert" className="mb-4 text-danger">
            {error.message}
          </p>
        ) : null}
        <div className="flex flex-wrap gap-2">
          <Button type="submit" loading={pending}>
            Lưu
          </Button>
          <Button type="button" variant="outline" disabled={pending} onClick={onCancel}>
            Huỷ sửa
          </Button>
        </div>
      </form>
    </Card>
  )
}
