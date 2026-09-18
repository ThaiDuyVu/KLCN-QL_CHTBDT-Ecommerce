import { useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router';
import { projectConfig } from '../config/projectConfig';
import { useAuth } from '../hooks/useAuth';
import './LoginPage.css';

const REMEMBERED_USERNAME_KEY = 'dien-viet.remembered-username';

function getRememberedUsername() {
  try {
    return localStorage.getItem(REMEMBERED_USERNAME_KEY) || '';
  } catch {
    return '';
  }
}

function loginErrorMessage(error) {
  if (error.status === 401) return 'Tên đăng nhập hoặc mật khẩu không đúng, hoặc tài khoản không hoạt động.';
  if (error.status === 403) return 'Yêu cầu bị từ chối. Vui lòng tải lại trang để lấy phiên bảo mật mới.';
  if (error.status === 400) return 'Vui lòng kiểm tra tên đăng nhập và mật khẩu.';
  if (error.status >= 500) return 'Máy chủ đang gặp sự cố. Vui lòng thử lại sau.';
  return error.message || 'Không thể đăng nhập. Vui lòng thử lại.';
}

export default function LoginPage() {
  const { isAuthenticated, isLoading, sessionError, signIn } = useAuth();
  const [username, setUsername] = useState(getRememberedUsername);
  const [password, setPassword] = useState('');
  const [rememberUsername, setRememberUsername] = useState(() => Boolean(getRememberedUsername()));
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const location = useLocation();
  const from = location.state?.from;
  const safePath = typeof from?.pathname === 'string' && from.pathname.startsWith('/')
    && !from.pathname.startsWith('//') && !from.pathname.includes('\\') && from.pathname !== '/login';
  const destination = safePath ? `${from.pathname}${from.search || ''}${from.hash || ''}` : '/account';

  if (isAuthenticated) return <Navigate to={destination} replace />;
  async function handleSubmit(event) {
    event.preventDefault();
    if (isSubmitting || isLoading) return;
    setError('');
    setNotice('');
    if (!username.trim() || !password.trim()) {
      setError('Vui lòng nhập tên đăng nhập và mật khẩu.');
      return;
    }
    setIsSubmitting(true);
    try {
      await signIn({ username: username.trim(), password });
      try {
        if (rememberUsername) localStorage.setItem(REMEMBERED_USERNAME_KEY, username.trim());
        else localStorage.removeItem(REMEMBERED_USERNAME_KEY);
      } catch { /* Storage may be disabled; it must not prevent sign-in. */ }
      setPassword('');
    } catch (requestError) {
      setError(loginErrorMessage(requestError));
      setPassword('');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="login-screen">
      <section className="login-card" aria-labelledby="login-title">
        <h1 id="login-title">Chào mừng trở lại</h1>
        <p className="login-subtitle">Đăng nhập để quản lý đơn hàng và tài khoản của bạn.</p>
        <form className="login-form" onSubmit={handleSubmit} aria-busy={isSubmitting || isLoading}>
          <fieldset disabled={isSubmitting || isLoading}>
            <legend className="sr-only">Thông tin đăng nhập</legend>
            <label className="sr-only" htmlFor="username">Tên đăng nhập</label>
            <input id="username" name="username" type="text" autoComplete="username" autoCapitalize="none"
              spellCheck={false} placeholder="Tên đăng nhập" value={username} required
              onChange={(event) => setUsername(event.target.value)} aria-describedby={error ? 'login-error' : undefined} />
            <label className="sr-only" htmlFor="password">Mật khẩu</label>
            <input id="password" name="password" type="password" autoComplete="current-password"
              placeholder="Mật khẩu" value={password} required
              onChange={(event) => setPassword(event.target.value)} aria-describedby={error ? 'login-error' : undefined} />
            <div className="login-options">
              <label className="remember-username" htmlFor="remember-username">
                <input id="remember-username" type="checkbox" checked={rememberUsername}
                  onChange={(event) => setRememberUsername(event.target.checked)} />
                <span>Ghi nhớ tên đăng nhập</span>
              </label>
              <button type="button" className="login-text-button"
                onClick={() => setNotice('Khôi phục mật khẩu chưa được hỗ trợ trong luồng auth hiện tại. Vui lòng liên hệ quản trị viên cửa hàng.')}>
                Quên mật khẩu?
              </button>
            </div>
            {error && <p id="login-error" className="auth-alert" role="alert">{error}</p>}
            <button className="login-submit" type="submit">
              {isLoading ? 'Đang kiểm tra phiên…' : isSubmitting ? 'Đang đăng nhập…' : 'Đăng nhập'}
            </button>
          </fieldset>
        </form>
        {sessionError && !error && <p className="login-session-note" role="status">Chưa kiểm tra được phiên cũ. Bạn có thể thử đăng nhập lại.</p>}
        <p className="login-signup">Mới đến {projectConfig.appName}?{' '}
          <button type="button" className="login-text-button"
            onClick={() => setNotice('Đăng ký công khai chưa được hỗ trợ trong luồng auth hiện tại. Vui lòng liên hệ cửa hàng để được cấp tài khoản.')}>
            Tạo tài khoản
          </button>
        </p>
        {notice && <p className="login-notice" role="status">{notice}</p>}
        <p className="login-policy">Chỉ ghi nhớ tên đăng nhập trên thiết bị này.<br />Thời hạn phiên đăng nhập do máy chủ quản lý.</p>
        <Link className="login-home-link" to="/">← Về trang chủ</Link>
      </section>
    </main>
  );
}
