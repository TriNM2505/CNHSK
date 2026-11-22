import { Check, Circle, X } from 'lucide-react'

import { cn } from '@/shared/lib/cn'

import { passwordRules } from '../passwordValidation'

interface PasswordRulesProps {
  id: string
  /** Đã bấm gửi: điều kiện còn thiếu chuyển thành lỗi. */
  invalid?: boolean
  password: string
}

/** Điều kiện mật khẩu, tự đánh dấu khi đang gõ. */
export function PasswordRules({ id, invalid = false, password }: PasswordRulesProps) {
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-body-sm" id={id}>
      {passwordRules(password).map(({ label, met }) => {
        const Icon = met ? Check : invalid ? X : Circle
        return (
          <li
            className={cn(
              'flex items-center gap-1.5',
              met ? 'text-success' : invalid ? 'font-semibold text-danger' : 'text-ink-secondary',
            )}
            key={label}
          >
            <Icon aria-hidden="true" className={cn('shrink-0', met || invalid ? 'size-4' : 'size-2')} />
            {label}
            <span className="sr-only">{met ? ' (đạt)' : ' (chưa đạt)'}</span>
          </li>
        )
      })}
    </ul>
  )
}
