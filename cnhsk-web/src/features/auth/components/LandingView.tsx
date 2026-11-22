import {
  AudioLines,
  BookOpenText,
  Brush,
  ClipboardCheck,
  FolderKanban,
  Gamepad2,
  Images,
  Languages,
  MessagesSquare,
  MonitorSmartphone,
  UserRoundCheck,
} from 'lucide-react'
import type { ReactNode } from 'react'

import advisorImage from '../assets/landing/advisor.webp'
import courseChineseImage from '../assets/landing/course-chinese.webp'
import courseHskImage from '../assets/landing/course-hsk.webp'
import heroBackground from '../assets/landing/hero-bg.webp'
import heroImage from '../assets/landing/hero.webp'
import lmsPreviewImage from '../assets/landing/lms-preview.webp'
import owlImage from '../assets/landing/owl-contact.webp'
import targetIcon from '../assets/landing/target-icon.webp'
import type { RoleArea } from '../roleAreas'
import { HocBaLogo } from '@/shared/brand'
import { Button, Card, Skeleton, StatusBadge } from '@/shared/ui'

export type LandingViewState = 'loading' | 'empty' | 'error' | 'ready'

export type LandingViewProps = {
  areas?: readonly RoleArea[]
  displayName?: string
  onRetry: () => void
  state: LandingViewState
}

/** Hoc Ba's own site; course, advisor and consulting links point there. */
const HOC_BA_URL = 'https://hoc-ba.edu.vn/'

const HERO_PILLARS = [
  'Lộ trình học cá nhân hoá theo năng lực',
  'Mentor học tập đồng hành 1–1',
  'Chương trình chuẩn HSK 3.0 & thực chiến',
] as const

const COURSES = [
  {
    description:
      'Chương trình luyện thi theo format HSK 3.0, ứng dụng công nghệ AI và lộ trình cá nhân hoá, giúp người học thi đỗ HSK hiệu quả và dùng tiếng Trung vững vàng. Phù hợp với người cần chứng chỉ để du học, xin việc và phát triển sự nghiệp.',
    image: courseHskImage,
    title: 'Học Bá HSK 3.0',
  },
  {
    description:
      'Khoá tiếng Trung cho người đi làm bận rộn, mục tiêu cốt lõi là dùng được tiếng Trung ngay trong công việc hằng ngày: bài học ngắn, rõ ràng, thực dụng; bài tập tinh gọn nhưng hiệu quả.',
    image: courseChineseImage,
    title: 'Học Bá Chinese',
  },
] as const

const METHODS: readonly { description: string; icon: ReactNode; title: string }[] = [
  {
    description: 'Học bằng thực hành thông qua các dự án và tình huống thực tế.',
    icon: <FolderKanban aria-hidden="true" className="size-6" />,
    title: 'Project-based Learning',
  },
  {
    description: 'Học qua hình ảnh như flashcard, video… giúp nhớ nhanh và nhớ lâu hơn.',
    icon: <Images aria-hidden="true" className="size-6" />,
    title: 'Visual E-learning',
  },
  {
    description: 'Học chủ động qua hoạt động giao tiếp, tranh luận và xử lý tình huống.',
    icon: <MessagesSquare aria-hidden="true" className="size-6" />,
    title: 'Active E-learning',
  },
  {
    description: 'LMS tích hợp AI: nhắc học thông minh, chấm chữa tự động, theo sát lộ trình.',
    icon: <MonitorSmartphone aria-hidden="true" className="size-6" />,
    title: 'Công nghệ LMS độc quyền',
  },
  {
    description: 'Mentor học thuật theo sát, giải đáp và nhắc bài trong suốt khoá học.',
    icon: <UserRoundCheck aria-hidden="true" className="size-6" />,
    title: 'Mentor học thuật đồng hành 24/7',
  },
]

const COMMITMENTS = [
  {
    description:
      'Học viên không đạt chuẩn đầu ra được học lại và mentor hỗ trợ miễn phí, chỉ cần tuân thủ đúng lộ trình đã thống nhất.',
    title: 'Cam kết đầu ra rõ ràng, không đạt học lại miễn phí',
  },
  {
    description:
      'Mỗi học viên đều được thi đánh giá đầu vào với giảng viên và xây dựng lộ trình theo mục tiêu thi cử và nhu cầu giao tiếp.',
    title: 'Lộ trình cá nhân hoá – học đủ, đúng cấp độ',
  },
  {
    description:
      'Lịch học linh hoạt nhiều khung giờ; có thể đổi giáo viên, đổi lớp, đổi giờ học và tham gia lớp bổ trợ khi cần.',
    title: 'Chăm sóc học viên tận tâm – lịch học linh hoạt',
  },
] as const

const LEARNING_MODES: readonly { description: string; icon: ReactNode; title: string }[] = [
  {
    description: 'Học từ vựng theo từng tình huống và chủ điểm gần gũi.',
    icon: <BookOpenText aria-hidden="true" className="size-6" />,
    title: 'Chủ đề',
  },
  {
    description: 'Hiểu cấu trúc câu bằng giải thích và ví dụ tiếng Việt.',
    icon: <Languages aria-hidden="true" className="size-6" />,
    title: 'Ngữ pháp',
  },
  {
    description: 'Luyện nghe, phân biệt âm và nói rõ hơn qua từng chặng.',
    icon: <AudioLines aria-hidden="true" className="size-6" />,
    title: 'Phát âm',
  },
  {
    description: 'Nhận diện và làm quen với cách viết chữ Hán đúng nét.',
    icon: <Brush aria-hidden="true" className="size-6" />,
    title: 'Chữ Hán',
  },
  {
    description: 'Làm đề mô phỏng theo cấp HSK và xem kết quả từng phần.',
    icon: <ClipboardCheck aria-hidden="true" className="size-6" />,
    title: 'Thi thử HSK',
  },
  {
    description: 'Ôn kiến thức bằng những lượt chơi ngắn và dễ bắt đầu.',
    icon: <Gamepad2 aria-hidden="true" className="size-6" />,
    title: 'Game',
  },
]

function GuestActions() {
  return (
    <div className="flex flex-col gap-3 sm:flex-row">
      <Button
        className="min-h-14 bg-accent-500 px-8 text-body font-bold text-brand-800 hover:bg-surface"
        to="/register"
      >
        Bắt đầu học
      </Button>
      <Button
        className="min-h-14 border-surface bg-transparent px-8 text-body font-semibold text-surface hover:border-surface hover:bg-surface hover:text-brand-700"
        to="/login"
        variant="outline"
      >
        Đăng nhập
      </Button>
    </div>
  )
}

function SessionRegion({ areas = [], displayName, onRetry, state }: LandingViewProps) {
  if (state === 'loading') {
    return (
      <div aria-label="Đang kiểm tra phiên đăng nhập" className="space-y-3">
        <Skeleton className="max-w-sm" variant="text" />
        <Skeleton className="max-w-md" variant="card" />
      </div>
    )
  }

  if (state === 'error') {
    return (
      <div className="space-y-3">
        <GuestActions />
        <div
          className="flex flex-wrap items-center gap-2 text-body-sm text-surface"
          role="status"
        >
          <span>Chưa kiểm tra được phiên đăng nhập.</span>
          <Button className="text-accent-500" onClick={onRetry} size="sm" variant="ghost">
            Thử lại
          </Button>
        </div>
      </div>
    )
  }

  if (!displayName) return <GuestActions />

  return (
    <section aria-labelledby="your-areas-title">
      <p className="text-body-sm text-ink-secondary">Xin chào, {displayName}</p>
      <h2 className="mt-1 text-h2 text-ink" id="your-areas-title">
        Khu vực của bạn
      </h2>
      {state === 'empty' || areas.length === 0 ? (
        <p className="mt-3 text-body text-ink-secondary">
          Tài khoản chưa được gán khu vực riêng. Bạn vẫn có thể xem phần giới thiệu bên dưới.
        </p>
      ) : (
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          {areas.map((area) => (
            <Card
              className="flex min-h-28 flex-col items-start justify-between gap-3 p-4"
              key={area.role}
            >
              <div>
                <h3 className="text-h3 text-ink">{area.label}</h3>
                {area.status === 'under-construction' ? (
                  <StatusBadge className="mt-2" tone="info">
                    Đang xây dựng
                  </StatusBadge>
                ) : (
                  <p className="mt-1 text-body-sm text-ink-secondary">Khu vực đã sẵn sàng</p>
                )}
              </div>
              {area.status === 'available' && area.to ? (
                <Button size="sm" to={area.to} variant="outline">
                  Mở {area.label.toLocaleLowerCase('vi')}
                </Button>
              ) : null}
            </Card>
          ))}
        </div>
      )}
    </section>
  )
}

function SectionHeading({
  eyebrow,
  id,
  lead,
  title,
}: {
  eyebrow: string
  id: string
  lead?: string
  title: string
}) {
  return (
    <div className="mx-auto max-w-reading text-center">
      <p className="text-label uppercase tracking-wide text-brand-600">{eyebrow}</p>
      <h2 className="mt-2 text-balance text-h1-sm text-ink sm:text-h1" id={id}>
        {title}
      </h2>
      {lead ? <p className="mt-3 text-body text-ink-secondary">{lead}</p> : null}
    </div>
  )
}

function HocBaLink({ children, label }: { children: ReactNode; label?: string }) {
  return (
    <Button aria-label={label} href={HOC_BA_URL} rel="noopener noreferrer" target="_blank">
      {children}
    </Button>
  )
}

export function LandingView(props: LandingViewProps) {
  const isGuest = props.state !== 'loading' && !props.displayName

  return (
    <main className="min-w-0 bg-canvas">
      {/* Hero */}
      <section
        className="relative overflow-hidden bg-brand-700 bg-cover bg-center text-surface lg:flex lg:min-h-[calc(100dvh-6rem)] lg:items-center"
        style={{ backgroundImage: `url(${heroBackground})` }}
      >
        <div className="relative z-10 mx-auto w-full max-w-content px-4 pb-8 pt-10 lg:py-16">
          <div className="min-w-0 lg:w-1/2 lg:pr-6">
            <p className="inline-flex rounded-pill bg-accent-500 px-4 py-1 text-label text-brand-800">
              Hệ thống giáo dục Hán ngữ trực tuyến
            </p>
            <h1 className="mt-5 text-balance text-display-sm sm:text-display xl:text-6xl xl:font-bold xl:leading-tight">
              Chương trình đào tạo toàn diện
            </h1>
            <p className="mt-2 text-h2 text-accent-500">Tiếng Trung chuẩn quốc tế</p>
            <ol className="mt-8 grid gap-4">
              {HERO_PILLARS.map((pillar, index) => (
                <li className="flex items-center gap-4 text-body lg:text-lg" key={pillar}>
                  <span
                    aria-hidden="true"
                    className="flex size-11 shrink-0 items-center justify-center rounded-pill border border-accent-500 text-label text-accent-500"
                  >
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  {pillar}
                </li>
              ))}
            </ol>
            <div
              className={
                props.displayName
                  ? 'mt-8 max-w-xl rounded-lg bg-surface p-5 text-ink shadow-overlay sm:p-6'
                  : 'mt-8'
              }
            >
              <SessionRegion {...props} />
            </div>
          </div>
        </div>
        {/* The light trail in the picture runs off its right side, so it sits against the page edge. */}
        <img
          alt="Học viên Học Bá học tiếng Trung trực tuyến trên máy tính"
          className="relative ml-auto w-full max-w-2xl lg:absolute lg:right-0 lg:top-1/2 lg:w-1/2 lg:max-w-none lg:-translate-y-1/2"
          decoding="async"
          height={515}
          src={heroImage}
          width={925}
        />
      </section>

      {/* About Hoc Ba */}
      <section aria-labelledby="about-title" id="gioi-thieu" className="px-4 lg:scroll-mt-8 py-12 sm:py-16">
        <div className="mx-auto grid max-w-content items-center gap-10 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,0.7fr)]">
          <div className="min-w-0">
            <p className="text-label uppercase tracking-wide text-brand-600">Giới thiệu</p>
            <h2 className="mt-2 text-h1-sm text-brand-700 sm:text-h1" id="about-title">
              Học Bá Education
            </h2>
            <p className="mt-2 text-label uppercase tracking-wide text-ink">
              Tổ chức giáo dục tiếng Trung thời đại mới
            </p>
            <div className="mt-4 max-w-reading space-y-3 text-body text-ink-secondary">
              <p>
                Học Bá Education cung cấp hệ thống khoá học tiếng Trung hiện đại, toàn diện, từ nền
                tảng đến nâng cao, gồm luyện thi HSK 1–6 và BCT (Business Chinese Test), cùng kỹ
                năng giao tiếp công sở – thương mại. Chương trình theo lộ trình chuẩn quốc tế, cập
                nhật liên tục theo các format mới nhất.
              </p>
              <p>
                Không chỉ đào tạo ngôn ngữ, Học Bá trang bị năng lực ứng dụng thực tiễn, kết nối học
                viên với môi trường học tập hiện đại, cộng đồng quốc tế và cơ hội nghề nghiệp rộng
                mở.
              </p>
            </div>
            <div className="mt-6">
              <HocBaLink>Xem thêm về Học Bá</HocBaLink>
            </div>
          </div>
          <img
            alt="Linh vật cú Học Bá bên máy tính"
            className="mx-auto w-full max-w-xs"
            decoding="async"
            height={320}
            loading="lazy"
            src={owlImage}
            width={377}
          />
        </div>
      </section>

      {/* Courses */}
      <section aria-labelledby="courses-title" id="khoa-hoc" className="bg-surface-subtle px-4 lg:scroll-mt-8 py-12 sm:py-16">
        <div className="mx-auto max-w-content">
          <SectionHeading
            eyebrow="Khoá học"
            id="courses-title"
            title="Khám phá khoá học của Học Bá"
          />
          <div className="mt-8 grid gap-6 lg:grid-cols-2">
            {COURSES.map((course) => (
              <Card
                className="flex min-w-0 flex-col items-center gap-4 p-6 text-center sm:flex-row sm:text-left"
                key={course.title}
              >
                <img
                  alt=""
                  className="w-40 shrink-0 sm:w-44"
                  decoding="async"
                  height={336}
                  loading="lazy"
                  src={course.image}
                  width={346}
                />
                <div className="min-w-0">
                  <h3 className="text-h2 text-brand-700">{course.title}</h3>
                  <p className="mt-2 text-body-sm text-ink-secondary">{course.description}</p>
                  <div className="mt-4">
                    <HocBaLink label={`Xem chi tiết khoá ${course.title}`}>
                      Xem chi tiết khoá học
                    </HocBaLink>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </div>
      </section>

      {/* Methods */}
      <section aria-labelledby="methods-title" id="phuong-phap" className="px-4 lg:scroll-mt-8 py-12 sm:py-16">
        <div className="mx-auto max-w-content">
          <SectionHeading
            eyebrow="Phương pháp"
            id="methods-title"
            lead="Bằng hệ sinh thái đào tạo hiện đại theo chuẩn đại học hàng đầu thế giới."
            title="Tối ưu hành trình học & luyện thi tiếng Trung"
          />
          <div className="mt-8 grid items-center gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
            <ul className="grid gap-4 sm:grid-cols-2">
              {METHODS.map((method) => (
                <li
                  className="rounded-md border border-brand-100 bg-surface p-4 shadow-sm"
                  key={method.title}
                >
                  <div className="flex size-11 items-center justify-center rounded-sm bg-brand-600 text-surface">
                    {method.icon}
                  </div>
                  <h3 className="mt-3 text-h3 text-ink">{method.title}</h3>
                  <p className="mt-1 text-body-sm text-ink-secondary">{method.description}</p>
                </li>
              ))}
            </ul>
            <img
              alt="Giao diện học tập LMS của Học Bá với mục tiêu hôm nay và study plan"
              className="mx-auto w-full max-w-lg"
              decoding="async"
              height={384}
              loading="lazy"
              src={lmsPreviewImage}
              width={448}
            />
          </div>
        </div>
      </section>

      {/* Commitments */}
      <section aria-labelledby="commitments-title" id="cam-ket" className="lg:scroll-mt-8 bg-brand-700 px-4 py-12 text-surface sm:py-16">
        <div className="mx-auto max-w-content">
          <div className="flex flex-col items-center gap-3 text-center">
            <img alt="" className="size-16" height={124} loading="lazy" src={targetIcon} width={124} />
            <h2 className="text-balance text-h1-sm sm:text-h1" id="commitments-title">
              Lấy chất lượng làm giá trị cốt lõi
            </h2>
            <p className="text-body text-accent-500">Cam kết 100% đầu ra – lộ trình cá nhân hoá</p>
          </div>
          <ol className="mt-8 grid gap-4 lg:grid-cols-3">
            {COMMITMENTS.map((item, index) => (
              <li className="rounded-md bg-surface p-5 text-ink shadow-md" key={item.title}>
                <span aria-hidden="true" className="text-h1 text-brand-600">
                  {String(index + 1).padStart(2, '0')}
                </span>
                <h3 className="mt-2 text-h3">{item.title}</h3>
                <p className="mt-2 text-body-sm text-ink-secondary">{item.description}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* Advisor */}
      <section aria-labelledby="advisor-title" id="co-van" className="px-4 lg:scroll-mt-8 py-12 sm:py-16">
        <div className="mx-auto max-w-content">
          <SectionHeading
            eyebrow="Học Bá hân hạnh giới thiệu"
            id="advisor-title"
            lead="Từ chuyên gia Hán ngữ hàng đầu thế giới."
            title="Cố vấn chuyên môn quốc tế độc quyền"
          />
          <div className="mt-8 grid items-center gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
            <img
              alt="Giáo sư Bao-Zhang He (何宝璋)"
              className="mx-auto w-full max-w-md"
              decoding="async"
              height={383}
              loading="lazy"
              src={advisorImage}
              width={512}
            />
            <div className="min-w-0">
              <h3 className="text-h2 text-brand-700">
                GS. Bao-Zhang He{' '}
                <span className="font-hanzi" lang="zh-CN">
                  何宝璋
                </span>
              </h3>
              <p className="mt-1 text-label text-ink">
                Nguyên Giám đốc Chương trình tiếng Trung tại Đại học Harvard
              </p>
              <ul className="mt-4 list-disc space-y-2 pl-5 text-body text-ink-secondary">
                <li>Chủ tịch Hiệp hội Giáo viên Tiếng Trung tại Hoa Kỳ (CLTA) nhiệm kỳ 2019–2020.</li>
                <li>
                  Đồng biên tập cuốn <em>Pedagogical Grammar and Grammar Pedagogy in Chinese as a
                  Second Language</em> (2022).
                </li>
              </ul>
              <div className="mt-6">
                <HocBaLink>Xem hồ sơ chuyên gia</HocBaLink>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* CNHSK practice platform */}
      <section
        aria-labelledby="learning-modes-title"
        id="luyen-tap"
        className="lg:scroll-mt-8 bg-surface-subtle px-4 py-12 sm:py-16"
      >
        <div className="mx-auto max-w-content">
          <SectionHeading
            eyebrow="Nền tảng luyện tập CNHSK"
            id="learning-modes-title"
            lead="Ngoài giờ học, bạn tự luyện mỗi ngày trên CNHSK. Các chức năng học cần tài khoản và vai trò phù hợp."
            title="Học theo cách phù hợp với bạn"
          />
          <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {LEARNING_MODES.map((mode) => (
              <Card className="p-5" key={mode.title}>
                <div className="flex size-11 items-center justify-center rounded-sm bg-brand-50 text-brand-700">
                  {mode.icon}
                </div>
                <h3 className="mt-4 text-h3 text-ink">{mode.title}</h3>
                <p className="mt-2 text-body-sm text-ink-secondary">{mode.description}</p>
              </Card>
            ))}
          </div>
        </div>
      </section>

      {/* Call to action, guests only */}
      {isGuest ? (
        <section aria-labelledby="cta-title" className="px-4 py-12 sm:py-16">
          <div className="mx-auto flex max-w-content flex-col items-center gap-4 rounded-lg bg-brand-600 p-8 text-center text-surface shadow-md">
            <h2 className="text-balance text-h1-sm sm:text-h1" id="cta-title">
              Có người đi cùng, đến đích mới thôi
            </h2>
            <p className="max-w-reading text-body">
              Đăng ký tư vấn lộ trình với Học Bá hoặc tạo tài khoản CNHSK để bắt đầu tự luyện ngay
              hôm nay.
            </p>
            <div className="flex flex-col gap-3 sm:flex-row">
              <Button className="bg-accent-500 text-brand-800 hover:bg-accent-500" to="/register">
                Tạo tài khoản CNHSK
              </Button>
              <Button
                href={HOC_BA_URL}
                rel="noopener noreferrer"
                target="_blank"
                variant="outline"
              >
                Đăng ký tư vấn Học Bá
              </Button>
            </div>
          </div>
        </section>
      ) : null}

      {/* Footer */}
      <footer className="bg-brand-800 px-4 py-10 text-surface">
        <div className="mx-auto grid max-w-content gap-8 sm:grid-cols-2 lg:grid-cols-3">
          <div className="space-y-3">
            <HocBaLogo className="h-12" inverse />
            <p className="text-body-sm">
              Học Bá – Hệ thống giáo dục Hán ngữ trực tuyến uy tín top đầu Việt Nam
            </p>
          </div>
          <div className="space-y-1 text-body-sm">
            <p className="text-label text-accent-500">Thông tin liên hệ</p>
            <p>Toà Hoà Phát, 257 Giải Phóng, Bạch Mai, Hà Nội</p>
            <p>info@hoc-ba.edu.vn</p>
            <p>0888 861 786</p>
          </div>
          <div className="space-y-1 text-body-sm">
            <p className="text-label text-accent-500">Thông tin pháp lý</p>
            <p>Công ty Cổ phần GD & ĐT Học Bá</p>
            <p>Mã số thuế: 0110883975</p>
          </div>
        </div>
      </footer>
    </main>
  )
}
