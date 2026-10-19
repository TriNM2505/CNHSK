import { CircleAlert, Eye, EyeOff } from 'lucide-react'
import {
  cloneElement,
  forwardRef,
  isValidElement,
  useState,
  type InputHTMLAttributes,
  type ReactElement,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react'

import { cn } from '@/shared/lib/cn'

interface DescribedControlProps {
  'aria-describedby'?: string
  'aria-invalid'?: boolean
  'aria-required'?: boolean
  id?: string
}

export interface FormFieldProps {
  children: ReactElement<DescribedControlProps>
  className?: string
  error?: string
  hint?: string
  htmlFor: string
  label: ReactNode
  required?: boolean
}

export function FormField({
  children,
  className,
  error,
  hint,
  htmlFor,
  label,
  required = false,
}: FormFieldProps) {
  const hintId = hint ? `${htmlFor}-hint` : undefined
  const errorId = error ? `${htmlFor}-error` : undefined
  const describedBy = [children.props['aria-describedby'], hintId, errorId]
    .filter(Boolean)
    .join(' ')
  const control = isValidElement(children)
    ? cloneElement(children, {
        'aria-describedby': describedBy || undefined,
        'aria-invalid': error ? true : children.props['aria-invalid'],
        'aria-required': required || children.props['aria-required'] || undefined,
        id: children.props.id ?? htmlFor,
      })
    : children

  return (
    <div className={cn('mb-4 flex flex-col gap-1.5', className)}>
      <label className="text-label text-ink" htmlFor={htmlFor}>
        {label}
        {required ? (
          <span aria-hidden="true" className="ml-0.5 text-danger">
            *
          </span>
        ) : null}
      </label>
      {control}
      {hint ? (
        <p className="text-body-sm text-ink-secondary" id={hintId}>
          {hint}
        </p>
      ) : null}
      {error ? (
        <p className="flex items-start gap-1 text-body-sm font-semibold text-danger" id={errorId}>
          <CircleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          <span>{error}</span>
        </p>
      ) : null}
    </div>
  )
}

const controlClasses =
  'min-h-control w-full rounded-sm border border-line bg-surface px-3 font-sans text-body text-ink placeholder:text-ink-muted focus:border-brand-600 focus:outline-none focus:ring-2 focus:ring-focus focus:ring-offset-2 disabled:cursor-not-allowed disabled:bg-canvas disabled:text-ink-muted aria-invalid:border-danger'

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  function Input({ className, ...props }, ref) {
    return <input {...props} ref={ref} className={cn(controlClasses, className)} />
  },
)

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(
  function Select({ className, ...props }, ref) {
    return <select {...props} ref={ref} className={cn(controlClasses, className)} />
  },
)

export const Textarea = forwardRef<
  HTMLTextAreaElement,
  TextareaHTMLAttributes<HTMLTextAreaElement>
>(function Textarea({ className, ...props }, ref) {
  return (
    <textarea
      {...props}
      ref={ref}
      className={cn(controlClasses, 'min-h-32 resize-y py-3', className)}
    />
  )
})

export interface PasswordInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  hideLabel?: string
  showLabel?: string
}

export const PasswordInput = forwardRef<HTMLInputElement, PasswordInputProps>(
  function PasswordInput(
    { className, hideLabel = 'Ẩn mật khẩu', showLabel = 'Hiện mật khẩu', ...props },
    ref,
  ) {
    const [visible, setVisible] = useState(false)
    const toggleLabel = visible ? hideLabel : showLabel

    return (
      <div className="relative">
        <input
          {...props}
          ref={ref}
          className={cn(controlClasses, 'pr-16', className)}
          type={visible ? 'text' : 'password'}
        />
        <button
          aria-label={toggleLabel}
          className="absolute right-1 top-1/2 inline-flex min-h-touch min-w-touch -translate-y-1/2 items-center justify-center rounded-sm text-ink-secondary hover:bg-brand-50 hover:text-brand-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
          onClick={() => setVisible((current) => !current)}
          type="button"
        >
          {visible ? (
            <EyeOff aria-hidden="true" className="size-5" />
          ) : (
            <Eye aria-hidden="true" className="size-5" />
          )}
        </button>
      </div>
    )
  },
)
