import {
  BookOpen,
  BookOpenText,
  Brush,
  ClipboardCheck,
  FilePenLine,
  ListTree,
  NotebookPen,
  RotateCcw,
  Search,
  TrendingUp,
  FileText,
  GitBranch,
  Headphones,
  CreditCard,
  Flag,
  Layers,
  Library,
  ListChecks,
  Gamepad2,
  Languages,
  LayoutDashboard,
  Megaphone,
  Mic2,
  PenTool,
  ReceiptText,
  Settings,
  ShieldCheck,
  Trophy,
  Upload,
  UsersRound,
} from 'lucide-react'
import type { ComponentType, SVGProps } from 'react'

import type { RoleCode } from '@/features/auth/types'

export type NavigationItem = {
  activePrefixes: readonly string[]
  /** Active only on the listed paths themselves, for a parent path whose children have their own items. */
  activeExact?: boolean
  /** Paths under a prefix that belong to another item, so this one stays dark there. */
  activeExcept?: readonly string[]
  /** Sidebar section heading; consecutive items with the same group share one heading. */
  group?: string
  icon: ComponentType<SVGProps<SVGSVGElement>>
  label: string
  mobileLabel?: string
  roles?: readonly RoleCode[]
  role?: RoleCode
  mobilePlacement?: 'more'
  to: string
}

export const learnerNavigation: readonly NavigationItem[] = [
  {
    activeExact: true,
    activePrefixes: ['/learn'],
    icon: LayoutDashboard,
    label: 'Bảng học tập',
    mobileLabel: 'Bảng học',
    to: '/learn',
  },
  // The four skills of HSK 1–3 come first; knowledge, review and tools follow in their own groups.
  {
    activePrefixes: ['/learn/listening'],
    group: 'Kỹ năng',
    icon: Headphones,
    label: 'Nghe',
    to: '/learn/listening',
  },
  {
    activePrefixes: ['/learn/speaking'],
    group: 'Kỹ năng',
    icon: Mic2,
    label: 'Luyện nói',
    to: '/learn/speaking',
  },
  {
    activePrefixes: ['/learn/reading'],
    group: 'Kỹ năng',
    icon: BookOpenText,
    label: 'Đọc',
    to: '/learn/reading',
  },
  {
    activePrefixes: ['/learn/writing'],
    group: 'Kỹ năng',
    icon: FilePenLine,
    label: 'Viết',
    mobilePlacement: 'more',
    to: '/learn/writing',
  },
  {
    activePrefixes: ['/learn/topics'],
    group: 'Kiến thức',
    icon: BookOpen,
    label: 'Chủ đề',
    mobilePlacement: 'more',
    to: '/learn/topics',
  },
  {
    activePrefixes: ['/learn/grammar'],
    group: 'Kiến thức',
    icon: ListTree,
    label: 'Ngữ pháp',
    mobilePlacement: 'more',
    to: '/learn/grammar',
  },
  {
    activePrefixes: ['/learn/characters'],
    group: 'Kiến thức',
    icon: Brush,
    label: 'Chữ Hán',
    mobilePlacement: 'more',
    to: '/learn/characters/recognition',
  },
  {
    activePrefixes: ['/learn/review'],
    group: 'Ôn và kiểm tra',
    icon: RotateCcw,
    label: 'Ôn bài đến hạn',
    mobileLabel: 'Ôn bài',
    mobilePlacement: 'more',
    to: '/learn/review',
  },
  {
    activePrefixes: ['/learn/exams', '/learn/placement'],
    group: 'Ôn và kiểm tra',
    icon: ClipboardCheck,
    label: 'Thi thử',
    mobilePlacement: 'more',
    to: '/learn/exams',
  },
  {
    activePrefixes: ['/learn/progress'],
    group: 'Ôn và kiểm tra',
    icon: TrendingUp,
    label: 'Tiến độ',
    mobilePlacement: 'more',
    to: '/learn/progress',
  },
  {
    activePrefixes: ['/dictionary'],
    group: 'Công cụ',
    icon: Search,
    label: 'Tra cứu',
    mobilePlacement: 'more',
    to: '/dictionary',
  },
  {
    activePrefixes: ['/translate'],
    group: 'Công cụ',
    icon: Languages,
    label: 'Dịch',
    mobilePlacement: 'more',
    to: '/translate',
  },
  {
    activePrefixes: ['/library/decks'],
    group: 'Công cụ',
    icon: Layers,
    label: 'Bộ thẻ',
    mobilePlacement: 'more',
    to: '/library/decks',
  },
  {
    activePrefixes: ['/library/notes'],
    group: 'Công cụ',
    icon: NotebookPen,
    label: 'Sổ tay',
    mobilePlacement: 'more',
    to: '/library/notes',
  },
  {
    activePrefixes: ['/references'],
    group: 'Công cụ',
    icon: Library,
    label: 'Tài liệu tham khảo',
    mobileLabel: 'Tham khảo',
    mobilePlacement: 'more',
    to: '/references/media',
  },
  {
    activePrefixes: ['/grading'],
    icon: PenTool,
    label: 'Chấm bài',
    mobilePlacement: 'more',
    to: '/grading',
  },
]

export const learnerCommunityNavigation: readonly NavigationItem[] = [
  {
    activePrefixes: ['/community/quizzes'],
    icon: ListChecks,
    label: 'Quiz',
    to: '/community/quizzes',
  },
  {
    activePrefixes: ['/contests'],
    icon: Trophy,
    label: 'Cuộc thi',
    to: '/contests',
  },
  {
    activePrefixes: ['/leaderboards'],
    icon: Trophy,
    label: 'Bảng xếp hạng',
    mobilePlacement: 'more',
    to: '/leaderboards/topics',
  },
]

export const gameNavigationItem = {
  icon: Gamepad2,
  label: 'Game',
} as const

export const adminNavigation: readonly NavigationItem[] = [
  {
    activePrefixes: ['/content/ai-review'],
    icon: ListChecks,
    label: 'Duyệt câu hỏi AI',
    mobileLabel: 'Duyệt AI',
    role: 'TEACHER',
    to: '/content/ai-review',
  },
  {
    activePrefixes: ['/admin/ai-runs'],
    icon: ListChecks,
    label: 'Lô AI',
    role: 'ADMIN',
    to: '/admin/ai-runs',
  },
  {
    activePrefixes: ['/content/questions'],
    icon: Library,
    label: 'Kho câu hỏi',
    mobileLabel: 'Câu hỏi',
    roles: ['CONTENT_EDITOR', 'TEACHER'],
    to: '/content/questions',
  },
  {
    activePrefixes: ['/content/exams'],
    icon: FileText,
    label: 'Quản lý đề thi',
    mobileLabel: 'Đề thi',
    role: 'CONTENT_EDITOR',
    to: '/content/exams',
  },
  {
    activePrefixes: ['/admin/settings'],
    icon: Settings,
    label: 'Cài đặt hệ thống',
    mobileLabel: 'Cài đặt',
    role: 'ADMIN',
    to: '/admin/settings',
  },
  {
    activePrefixes: ['/content/topics/prerequisites'],
    icon: GitBranch,
    label: 'Tiên quyết chủ đề',
    mobileLabel: 'Tiên quyết',
    role: 'CONTENT_EDITOR',
    to: '/content/topics/prerequisites',
  },
  {
    activePrefixes: ['/content/contests'],
    icon: Trophy,
    label: 'Cuộc thi',
    role: 'CONTENT_EDITOR',
    to: '/content/contests',
  },
  {
    activePrefixes: ['/teacher/grading'],
    icon: PenTool,
    label: 'Chấm bài viết',
    role: 'TEACHER',
    to: '/teacher/grading',
  },
  {
    activePrefixes: ['/content/references'],
    icon: BookOpen,
    label: 'Tài nguyên tham khảo',
    mobileLabel: 'Tham khảo',
    role: 'CONTENT_EDITOR',
    to: '/content/references',
  },
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
  {
    activePrefixes: ['/admin/card-batches'],
    icon: CreditCard,
    label: 'Lô mã thẻ',
    mobileLabel: 'Lô thẻ',
    role: 'ADMIN',
    to: '/admin/card-batches',
  },
  {
    activePrefixes: ['/admin/plans'],
    icon: Layers,
    label: 'Gói dịch vụ',
    mobileLabel: 'Gói',
    role: 'ADMIN',
    to: '/admin/plans',
  },
  {
    activePrefixes: ['/admin/ledger'],
    icon: ReceiptText,
    label: 'Sổ cái điểm',
    mobileLabel: 'Sổ cái',
    role: 'ADMIN',
    to: '/admin/ledger',
  },
  {
    activeExcept: ['/moderation/posts/new'],
    activePrefixes: ['/moderation/posts'],
    icon: ShieldCheck,
    label: 'Bài chờ duyệt',
    role: 'MODERATOR',
    to: '/moderation/posts',
  },
  {
    activeExact: true,
    activePrefixes: ['/moderation/posts/new'],
    icon: Megaphone,
    label: 'Đăng bài cộng đồng',
    mobileLabel: 'Đăng bài',
    role: 'MODERATOR',
    to: '/moderation/posts/new',
  },
  {
    activePrefixes: ['/moderation/reports'],
    icon: Flag,
    label: 'Báo cáo vi phạm',
    role: 'MODERATOR',
    to: '/moderation/reports',
  },
]

export function isNavigationItemActive(pathname: string, item: NavigationItem): boolean {
  if (item.activeExcept?.includes(pathname)) return false
  return item.activePrefixes.some(
    (prefix) => pathname === prefix || (!item.activeExact && pathname.startsWith(`${prefix}/`)),
  )
}
