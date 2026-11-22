import type { ReactNode } from 'react'

import { HanziText, PageHeader } from '@/shared/ui'

interface AuthHeadingProps {
  children?: ReactNode
  /** Hai chữ Hán khắc trên con dấu, gợi nội dung của màn. */
  seal: string
  title: string
}

/** Tiêu đề màn Auth kèm con dấu son như lạc khoản trên tranh thủy mặc. */
export function AuthHeading({ children, seal, title }: AuthHeadingProps) {
  return (
    <PageHeader
      actions={
        <span aria-hidden="true" className="auth-seal">
          {[...seal].map((character) => (
            <HanziText className="text-h3 leading-6 text-surface" key={character} size="sm">
              {character}
            </HanziText>
          ))}
        </span>
      }
      className="mb-5 flex-nowrap"
      subtitle={children ? <span className="block text-body-sm">{children}</span> : undefined}
      title={<span className="block text-h1-sm">{title}</span>}
    />
  )
}
