import { Briefcase, GraduationCap, LayoutGrid, Search, UsersRound } from 'lucide-react'
import type { ComponentType, SVGProps } from 'react'

import type { CurrentUser, RoleCode } from '@/features/auth/types'
import { adminNavigation } from '@/shared/layout/navigation'

export type PublicMenuLink = { label: string; to: string }

export type PublicMenu = {
  columns: readonly { links: readonly PublicMenuLink[]; title: string }[]
  icon: ComponentType<SVGProps<SVGSVGElement>>
  id: string
  label: string
  /** Where clicking the menu's own name goes; the columns open on hover or from the arrow. */
  to: string
  /** Shown under the columns when the links lead to screens behind sign-in. */
  note?: string
}

const aboutMenu: PublicMenu = {
  id: 'about',
  icon: LayoutGrid,
  label: 'Giới thiệu',
  to: '/#gioi-thieu',
  columns: [
    {
      title: 'Về Học Bá',
      links: [
        { label: 'Học Bá Education', to: '/#gioi-thieu' },
        { label: 'Phương pháp đào tạo', to: '/#phuong-phap' },
        { label: 'Cam kết đầu ra', to: '/#cam-ket' },
        { label: 'Cố vấn chuyên môn', to: '/#co-van' },
      ],
    },
    {
      title: 'Khoá học',
      links: [
        { label: 'Học Bá HSK 3.0', to: '/#khoa-hoc' },
        { label: 'Học Bá Chinese', to: '/#khoa-hoc' },
      ],
    },
    {
      title: 'Nền tảng CNHSK',
      links: [{ label: 'Các cách tự luyện', to: '/#luyen-tap' }],
    },
  ],
}

const practiceColumns: PublicMenu['columns'] = [
  {
    title: 'Học mỗi ngày',
    links: [
      { label: 'Từ vựng theo chủ đề', to: '/learn/topics' },
      { label: 'Ngữ pháp', to: '/learn/grammar' },
      { label: 'Phát âm', to: '/learn/pronunciation' },
      { label: 'Chữ Hán', to: '/learn/characters/recognition' },
    ],
  },
  {
    title: 'Luyện thi',
    links: [
      { label: 'Thi thử HSK', to: '/learn/exams' },
      { label: 'Quiz theo chủ đề', to: '/community/quizzes' },
    ],
  },
  {
    title: 'Hỗ trợ',
    links: [
      { label: 'Chấm bài viết', to: '/grading' },
      { label: 'Dịch câu, đoạn văn', to: '/translate' },
    ],
  },
]

/** A learner's own pages, put ahead of the practice links once signed in. */
const learnerOwnColumn: PublicMenu['columns'][number] = {
  title: 'Của bạn',
  links: [
    { label: 'Bảng học tập', to: '/learn' },
    { label: 'Ôn tập đến hạn', to: '/learn/review' },
    { label: 'Tiến độ học tập', to: '/learn/progress' },
  ],
}

const lookupMenu: PublicMenu = {
  id: 'lookup',
  icon: Search,
  label: 'Tra cứu',
  to: '/dictionary',
  columns: [
    {
      title: 'Từ điển Trung – Việt',
      links: [
        { label: 'Tra theo chữ Hán', to: '/dictionary?type=HANZI' },
        { label: 'Tra theo pinyin', to: '/dictionary?type=PINYIN' },
        { label: 'Tra theo nghĩa tiếng Việt', to: '/dictionary?type=MEANING' },
        { label: 'Tra theo bộ thủ, số nét', to: '/dictionary?type=RADICAL' },
      ],
    },
    {
      title: 'Tài liệu tham khảo',
      links: [
        { label: 'Video và podcast', to: '/references/media' },
        { label: 'Sách', to: '/references/books' },
      ],
    },
  ],
}

const communityMenu: PublicMenu = {
  id: 'community',
  icon: UsersRound,
  label: 'Cộng đồng',
  to: '/community',
  columns: [
    {
      title: 'Cùng học',
      links: [
        { label: 'Bảng tin và vinh danh', to: '/community' },
        { label: 'Cuộc thi', to: '/contests' },
      ],
    },
  ],
}

/** Staff areas in the order the work menu lists them. */
const staffColumns: readonly { role: RoleCode; title: string }[] = [
  { role: 'TEACHER', title: 'Giáo viên' },
  { role: 'CONTENT_EDITOR', title: 'Biên tập nội dung' },
  { role: 'MODERATOR', title: 'Kiểm duyệt' },
  { role: 'ADMIN', title: 'Quản trị' },
]

/** One column per staff role the user holds; a page shared by two roles shows under the first. */
function workMenu(roles: readonly RoleCode[]): PublicMenu | null {
  const listed = new Set<string>()
  const columns = staffColumns
    .filter(({ role }) => roles.includes(role))
    .map(({ role, title }) => ({
      title,
      links: adminNavigation
        .filter((item) => (item.roles ?? [item.role]).includes(role) && !listed.has(item.to))
        .map((item) => {
          listed.add(item.to)
          return { label: item.label, to: item.to }
        }),
    }))
    .filter(({ links }) => links.length > 0)

  const first = columns[0]?.links[0]
  return first ? { id: 'work', icon: Briefcase, label: 'Công việc', to: first.to, columns } : null
}

/** What the header offers: guests see what they can open, signed-in users also get their own pages. */
export function publicMenusFor(user: CurrentUser | null): readonly PublicMenu[] {
  const isLearner = user?.roles.includes('LEARNER') ?? false
  const practiceMenu: PublicMenu = {
    id: 'practice',
    icon: GraduationCap,
    label: 'Luyện tập',
    to: isLearner ? '/learn' : '/learn/topics',
    columns: isLearner ? [learnerOwnColumn, ...practiceColumns] : practiceColumns,
    note: user ? undefined : 'Cần đăng nhập. Chọn một mục, đăng nhập xong bạn vào thẳng mục đó.',
  }
  const work = user ? workMenu(user.roles) : null

  return [aboutMenu, practiceMenu, lookupMenu, communityMenu, ...(work ? [work] : [])]
}
