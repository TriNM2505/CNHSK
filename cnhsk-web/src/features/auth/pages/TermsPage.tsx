import { Card, PageHeader } from '@/shared/ui'

type Clause = { body: string; title: string }

const TERMS: readonly Clause[] = [
  {
    title: 'Về CNHSK',
    body: 'CNHSK là nền tảng tự luyện tiếng Trung và HSK. CNHSK là sản phẩm đồ án phục vụ học tập; kết quả thi thử chỉ để tham khảo, không thay thế chứng chỉ HSK.',
  },
  {
    title: 'Tài khoản',
    body: 'Mỗi email đăng ký một tài khoản. Bạn cung cấp email của chính mình, giữ kín mật khẩu và không cho người khác dùng chung.',
  },
  {
    title: 'Xác minh email',
    body: 'Để tạo tài khoản, bạn nhập mã được gửi tới email của mình. Tài khoản tạo trước đây chưa xác thực email vẫn học được, nhưng cần xác thực trước khi đăng bài, bình luận, báo cáo vi phạm, đăng ký cuộc thi và nhận nhắc học qua email.',
  },
  {
    title: 'Cách cư xử',
    body: 'Không đăng nội dung phản cảm, xúc phạm, lừa đảo, quảng cáo hoặc vi phạm bản quyền. Không gian lận trong thi, trò chơi và bảng xếp hạng. Không tìm cách phá hoại hoặc truy cập trái phép.',
  },
  {
    title: 'Nội dung bạn đăng',
    body: 'Bạn chịu trách nhiệm về nội dung của mình và cho phép CNHSK hiển thị nội dung đó trên nền tảng. Bài viết có thể được kiểm duyệt, ẩn hoặc gỡ.',
  },
  {
    title: 'Lượt dùng và gói dịch vụ',
    body: 'Một số tính năng có giới hạn lượt. Số lượt và điểm hiển thị trong tài khoản của bạn.',
  },
  {
    title: 'Nội dung do AI hỗ trợ',
    body: 'Bản dịch, trợ lý và một phần câu hỏi có AI hỗ trợ và có thể sai. Câu hỏi do AI soạn được giáo viên duyệt trước khi tới người học.',
  },
  {
    title: 'Khoá tài khoản',
    body: 'Tài khoản vi phạm có thể bị tạm khoá hoặc cấm, kèm lý do.',
  },
]

const PRIVACY: readonly Clause[] = [
  {
    title: 'Dữ liệu chúng tôi lưu',
    body: 'Email, tên hiển thị, ảnh đại diện (nếu có), cấp HSK; kết quả học, thi, trò chơi; bài viết, bình luận, ghi chú, bộ thẻ và bài nộp chấm. Mật khẩu chỉ lưu dạng mã hoá một chiều. Hệ thống dùng địa chỉ mạng (IP) để chống dò mật khẩu và lạm dụng.',
  },
  {
    title: 'Dùng để làm gì',
    body: 'Để bạn học và lưu tiến độ, gợi ý nội dung phù hợp, xếp hạng, gửi email xác thực, đặt lại mật khẩu và nhắc học, bảo vệ hệ thống.',
  },
  {
    title: 'Ai nhìn thấy',
    body: 'Tên hiển thị, bài viết, bình luận, bộ thẻ bạn công khai và thứ hạng được hiển thị công khai. Email không hiển thị công khai. Giáo viên xem bài bạn gửi chấm. Quản trị viên chỉ xem thông tin tài khoản khi cần xử lý.',
  },
  {
    title: 'Dịch vụ bên ngoài',
    body: 'Nội dung bạn gửi cho trợ lý hoặc mục dịch được chuyển tới nhà cung cấp AI để trả lời. Chúng tôi không gửi kèm tên, email hay mật khẩu. Bạn không nên nhập thông tin riêng tư vào đó. Email được gửi qua nhà cung cấp thư điện tử. Chúng tôi không bán dữ liệu của bạn.',
  },
  {
    title: 'Cookie',
    body: 'Cookie dùng để giữ đăng nhập, bảo vệ biểu mẫu và đếm lượt tra thử của khách. Không dùng cookie quảng cáo.',
  },
  {
    title: 'Quyền của bạn',
    body: 'Bạn sửa được tên, ảnh, cấp HSK và tắt nhắc học bất cứ lúc nào. Muốn xem, sửa hoặc xoá dữ liệu khác, liên hệ info@hoc-ba.edu.vn.',
  },
  {
    title: 'Thay đổi',
    body: 'Khi nội dung thay đổi đáng kể, chúng tôi sẽ thông báo trên nền tảng.',
  },
]

function ClauseList({
  clauses,
  heading,
  id,
  start,
}: {
  clauses: readonly Clause[]
  heading: string
  id: string
  start: number
}) {
  return (
    <section aria-labelledby={id}>
      <Card className="p-5 sm:p-8">
        <h2 className="text-h2 text-brand-800" id={id}>
          {heading}
        </h2>
        <ol className="mt-5 space-y-5" start={start}>
          {clauses.map(({ body, title }, index) => (
            <li key={title}>
              <h3 className="text-h3 text-ink">
                {start + index}. {title}
              </h3>
              <p className="mt-1 text-body text-ink-secondary">{body}</p>
            </li>
          ))}
        </ol>
      </Card>
    </section>
  )
}

export default function TermsPage() {
  return (
    <main className="mx-auto w-full max-w-reading space-y-6 px-4 py-8 pb-24 sm:px-6">
      <PageHeader
        subtitle="CNHSK · Học Bá Education · Hiệu lực từ 23/11/2026"
        title="Điều khoản sử dụng và chính sách dữ liệu"
      />
      <ClauseList clauses={TERMS} heading="Điều khoản sử dụng" id="terms" start={1} />
      <ClauseList
        clauses={PRIVACY}
        heading="Chính sách dữ liệu"
        id="privacy"
        start={TERMS.length + 1}
      />
    </main>
  )
}
