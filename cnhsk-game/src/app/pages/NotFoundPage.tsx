import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="core-page">
      <section aria-labelledby="not-found-title" className="core-panel core-center core-result">
        <span className="result-star" aria-hidden="true">404</span>
        <h1 id="not-found-title">Không tìm thấy trang</h1>
        <p>Bạn có thể trở về danh sách trò chơi để tiếp tục.</p>
        <div className="button-row">
          <Link to="/" className="primary-button">
            Về danh sách trò chơi
          </Link>
        </div>
      </section>
    </div>
  )
}