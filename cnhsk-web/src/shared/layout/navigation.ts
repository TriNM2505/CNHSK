import { BookOpen, Gamepad2, Languages, Mic2, PenTool, Upload, UsersRound } from 'lucide-react'
import type { ComponentType, SVGProps } from 'react'

import type { RoleCode } from '@/features/auth/types'

export type NavigationItem = {
  activePrefixes: readonly string[]
  icon: ComponentType<SVGProps<SVGSVGElement>>
  label: string
  role?: RoleCode
  to: string
}

export const learnerNavigation: readonly NavigationItem[] = [
  {
    activePrefixes: ['/learn/topics'],
    icon: BookOpen,
    label: 'Chủ đề',
    to: '/learn/topics',
  },
  {
    activePrefixes: ['/learn/grammar'],
    icon: Languages,
    label: 'Ngữ pháp',
    to: '/learn/grammar',
  },
  {
    activePrefixes: ['/learn/pronunciation'],
    icon: Mic2,
    label: 'Phát âm',
    to: '/learn/pronunciation',
  },
  {
    activePrefixes: ['/learn/characters'],
    icon: PenTool,
    label: 'Chữ Hán',
    to: '/learn/characters/recognition',
  },
]

export const gameNavigationItem = {
  icon: Gamepad2,
  label: 'Game',
} as const

export const adminNavigation: readonly NavigationItem[] = [
  {
    activePrefixes: ['/content/imports'],
    icon: Upload,
    label: 'Nhập dữ liệu',
    role: 'CONTENT_EDITOR',
    to: '/content/imports',
  },
  {
    activePrefixes: ['/admin/users'],
    icon: UsersRound,
    label: 'Người dùng',
    role: 'ADMIN',
    to: '/admin/users',
  },
]

export function isNavigationItemActive(pathname: string, item: NavigationItem): boolean {
  return item.activePrefixes.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  )
}
