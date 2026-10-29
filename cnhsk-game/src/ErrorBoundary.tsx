import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'

interface Props { children: ReactNode }
interface State { error: Error | null }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('CNHSK Arcade render error', error, info.componentStack)
  }

  render() {
    if (!this.state.error) return this.props.children
    return <main style={{ minHeight: '100vh', padding: '12vh 24px', background: '#f5f0e7', color: '#283442', textAlign: 'center' }}>
      <h1>Game gặp lỗi khi mở</h1>
      <p>Hãy tải lại trang. Nếu lỗi lặp lại, gửi thông báo bên dưới để mình sửa.</p>
      <pre style={{ maxWidth: 760, margin: '24px auto', padding: 20, background: '#fff', borderRadius: 12, overflowWrap: 'anywhere', whiteSpace: 'pre-wrap', textAlign: 'left' }}>{this.state.error.message}</pre>
      <button onClick={() => window.location.assign('/')} style={{ padding: '12px 22px', border: 0, borderRadius: 8, background: '#dc654c', color: '#fff', fontWeight: 700 }}>Về trang chọn game</button>
    </main>
  }
}
