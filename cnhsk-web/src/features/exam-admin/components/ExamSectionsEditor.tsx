import { useState } from 'react'
import { ArrowDown, ArrowUp, Plus, Settings, Trash2 } from 'lucide-react'
import { Button, ConfirmPanel, FormField, Input, Select } from '@/shared/ui'
import { move, newSection, removeSection, sectionLabels } from '../examForm'
import type { ExamForm, SectionType } from '../types'

export function ExamSectionsEditor({
  form,
  disabled,
  onChange,
}: {
  form: ExamForm
  disabled: boolean
  onChange: (form: ExamForm) => void
}) {
  const [removing, setRemoving] = useState<string>()
  function remove(key: string) {
    if (form.questions.some((q) => q.sectionKey === key)) setRemoving(key)
    else onChange(removeSection(form, key))
  }
  return (
    <section
      aria-label="Các phần thi"
      className="min-w-0 space-y-4 rounded-md border border-brand-100 bg-brand-50/30 p-5"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-h3 font-bold">
          <Settings aria-hidden="true" className="size-5 text-brand-700" />
          Các phần thi
        </h2>
        <Button
          variant="outline"
          className="border-brand-100 text-brand-700"
          disabled={disabled || form.sections.length >= 5}
          onClick={() => onChange({ ...form, sections: [...form.sections, newSection()] })}
        >
          <span className="inline-flex items-center gap-2">
            <Plus aria-hidden="true" className="size-4" />
            Thêm phần
          </span>
        </Button>
      </div>
      {form.sections.map((section, index) => (
        <fieldset
          key={section.key}
          disabled={disabled}
          draggable={!disabled}
          onDragStart={(event) => event.dataTransfer.setData('application/section', String(index))}
          onDragOver={(event) => {
            if (!disabled) event.preventDefault()
          }}
          onDrop={(event) => {
            event.preventDefault()
            const value = event.dataTransfer.getData('application/section')
            if (!disabled && /^\d+$/.test(value))
              onChange({ ...form, sections: move(form.sections, Number(value), index) })
          }}
          className="min-w-0 rounded-md border border-brand-100 bg-surface p-4"
        >
          <legend className="rounded-sm bg-brand-50 px-3 py-1 text-label text-brand-700">
            Phần {index + 1}
          </legend>
          <div className="grid min-w-0 gap-x-4 sm:grid-cols-2 lg:grid-cols-3">
            <FormField
              htmlFor={`section-name-${section.key}`}
              label={`Tên phần ${index + 1}`}
              required
            >
              <Input
                value={section.name}
                placeholder="Nhập tên phần…"
                onChange={(e) =>
                  onChange({
                    ...form,
                    sections: form.sections.map((s) =>
                      s.key === section.key ? { ...s, name: e.target.value } : s,
                    ),
                  })
                }
              />
            </FormField>
            <FormField
              htmlFor={`section-type-${section.key}`}
              label={`Loại phần ${index + 1}`}
              required
            >
              <Select
                value={section.type}
                onChange={(e) =>
                  onChange({
                    ...form,
                    sections: form.sections.map((s) =>
                      s.key === section.key ? { ...s, type: e.target.value as SectionType } : s,
                    ),
                  })
                }
              >
                <option value="">Bổ sung loại</option>
                {Object.entries(sectionLabels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </FormField>
            <FormField
              htmlFor={`section-duration-${section.key}`}
              label={`Thời lượng phần ${index + 1} (phút)`}
              required
            >
              <Input
                type="number"
                min={0}
                max={180}
                step={1}
                value={section.durationMin}
                placeholder="Nhập thời lượng…"
                onChange={(e) =>
                  onChange({
                    ...form,
                    sections: form.sections.map((s) =>
                      s.key === section.key ? { ...s, durationMin: e.target.value } : s,
                    ),
                  })
                }
              />
            </FormField>
          </div>
          {(!section.type || !section.durationMin) && (
            <p className="mb-3 text-body-sm text-ink-secondary">
              Bổ sung loại và thời lượng còn thiếu trước khi lưu; không tự điền dữ liệu đề nhập cũ.
            </p>
          )}
          <div className="flex flex-wrap gap-2 border-t border-line pt-4">
            <Button
              size="sm"
              variant="ghost"
              disabled={disabled || index === 0}
              aria-label={`Đưa phần ${index + 1} lên`}
              onClick={() => onChange({ ...form, sections: move(form.sections, index, index - 1) })}
            >
              <span className="inline-flex items-center gap-1">
                <ArrowUp aria-hidden="true" className="size-4" />
                Lên
              </span>
            </Button>
            <Button
              size="sm"
              variant="ghost"
              disabled={disabled || index === form.sections.length - 1}
              aria-label={`Đưa phần ${index + 1} xuống`}
              onClick={() => onChange({ ...form, sections: move(form.sections, index, index + 1) })}
            >
              <span className="inline-flex items-center gap-1">
                <ArrowDown aria-hidden="true" className="size-4" />
                Xuống
              </span>
            </Button>
            <Button
              size="sm"
              variant="danger"
              disabled={disabled || form.sections.length === 1}
              onClick={() => remove(section.key)}
            >
              <span className="inline-flex items-center gap-1">
                <Trash2 aria-hidden="true" className="size-4" />
                Bỏ phần {index + 1}
              </span>
            </Button>
          </div>
        </fieldset>
      ))}
      <ConfirmPanel
        open={!!removing}
        title="Bỏ phần khỏi nháp?"
        message="Các liên kết câu hỏi trong phần này cũng được bỏ khỏi nháp. Câu trong ngân hàng không bị xoá."
        danger
        confirmLabel="Bỏ phần và liên kết"
        onCancel={() => setRemoving(undefined)}
        onConfirm={() => {
          if (removing && !disabled) onChange(removeSection(form, removing))
          setRemoving(undefined)
        }}
      />
    </section>
  )
}
