import { LoaderCircle } from 'lucide-react'
import type { AnchorHTMLAttributes, ButtonHTMLAttributes, MouseEvent, ReactNode } from 'react'
import { Link, type LinkProps, type To } from 'react-router-dom'

import { cn } from '@/shared/lib/cn'

type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger'
type ButtonSize = 'sm' | 'md'

interface ButtonBaseProps {
  children: ReactNode
  className?: string
  disabled?: boolean
  loading?: boolean
  size?: ButtonSize
  variant?: ButtonVariant
}

type NativeButtonProps = ButtonBaseProps &
  Omit<ButtonHTMLAttributes<HTMLButtonElement>, keyof ButtonBaseProps | 'href'> & {
    href?: never
    to?: never
  }

type RouterButtonProps = ButtonBaseProps &
  Omit<LinkProps, keyof ButtonBaseProps | 'href' | 'to'> & {
    href?: never
    to: To
  }

type ExternalButtonProps = ButtonBaseProps &
  Omit<AnchorHTMLAttributes<HTMLAnchorElement>, keyof ButtonBaseProps | 'href'> & {
    href: string
    to?: never
  }

export type ButtonProps = NativeButtonProps | RouterButtonProps | ExternalButtonProps

const variantClasses: Record<ButtonVariant, string> = {
  primary: 'border-transparent bg-brand-600 text-surface hover:bg-brand-700',
  secondary: 'border-brand-100 bg-brand-50 text-brand-700 hover:bg-brand-100',
  outline: 'border-line bg-surface text-ink hover:border-brand-600 hover:text-brand-700',
  ghost:
    'border-transparent bg-transparent text-ink-secondary hover:bg-brand-50 hover:text-brand-700',
  danger: 'border-transparent bg-danger text-surface hover:opacity-90',
}

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'min-h-touch px-3 text-caption',
  md: 'min-h-control px-4 text-label',
}

const baseClasses =
  'inline-flex items-center justify-center gap-2 rounded-sm border font-sans no-underline transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 aria-disabled:pointer-events-none aria-disabled:cursor-not-allowed aria-disabled:opacity-50'

export function Button(props: ButtonProps) {
  const {
    children,
    className,
    disabled = false,
    loading = false,
    size = 'md',
    variant = 'primary',
    ...renderProps
  } = props
  const isDisabled = disabled || loading
  const classes = cn(baseClasses, variantClasses[variant], sizeClasses[size], className)
  const content = (
    <>
      {loading ? (
        <LoaderCircle aria-hidden="true" className="size-4 motion-safe:animate-spin" />
      ) : null}
      <span>{children}</span>
    </>
  )

  if ('to' in renderProps && renderProps.to !== undefined) {
    const { to, onClick, ...linkProps } = renderProps as Omit<
      RouterButtonProps,
      keyof ButtonBaseProps
    >
    const handleClick = (event: MouseEvent<HTMLAnchorElement>) => {
      if (isDisabled) {
        event.preventDefault()
        return
      }
      onClick?.(event)
    }

    return (
      <Link
        {...linkProps}
        aria-busy={loading || undefined}
        aria-disabled={isDisabled || undefined}
        className={classes}
        onClick={handleClick}
        to={to}
      >
        {content}
      </Link>
    )
  }

  if ('href' in renderProps && renderProps.href !== undefined) {
    const { href, onClick, rel, target, ...anchorProps } = renderProps as Omit<
      ExternalButtonProps,
      keyof ButtonBaseProps
    >
    const safeRel =
      target === '_blank'
        ? Array.from(new Set([...(rel?.split(/\s+/).filter(Boolean) ?? []), 'noreferrer'])).join(
            ' ',
          )
        : rel
    const handleClick = (event: MouseEvent<HTMLAnchorElement>) => {
      if (isDisabled) {
        event.preventDefault()
        return
      }
      onClick?.(event)
    }

    return (
      <a
        {...anchorProps}
        aria-busy={loading || undefined}
        aria-disabled={isDisabled || undefined}
        className={classes}
        href={href}
        onClick={handleClick}
        rel={safeRel}
        target={target}
      >
        {content}
      </a>
    )
  }

  const { type = 'button', ...buttonProps } = renderProps as Omit<
    NativeButtonProps,
    keyof ButtonBaseProps
  >

  return (
    <button
      {...buttonProps}
      aria-busy={loading || undefined}
      className={classes}
      disabled={isDisabled}
      type={type}
    >
      {content}
    </button>
  )
}
