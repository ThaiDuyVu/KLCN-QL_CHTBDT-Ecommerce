import { useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router';
import { projectConfig } from '../config/projectConfig';
import { useAuth } from '../hooks/useAuth';
import { postAuthDestination } from '../auth/postAuthDestination';
import './LoginPage.css';

// Same rules as RegisterRequest on the backend, which stays the source of truth.
const USERNAME_PATTERN = /^[A-Za-z0-9._-]{4,50}$/;
const PASSWORD_RULES = [
  { id: 'length', label: 'Ít nhất 8 ký tự', test: (value) => value.length >= 8 },
  { id: 'letter', label: 'Có ít nhất một chữ cái', test: (value) => /\p{L}/u.test(value) },
  { id: 'digit', label: 'Có ít nhất một chữ số', test: (value) => /\d/.test(value) },
];

function registerErrorMessage(error) {
  if (error.status === 409) return 'Tên đăng nhập đã được sử dụng. Vui lòng chọn tên khác.';
  if (error.status === 403) return 'Yêu cầu bị từ chối. Vui lòng tải lại trang để lấy phiên bảo mật mới.';
  if (error.status === 400) return error.message || 'Vui lòng kiểm tra lại thông tin đăng ký.';
  if (error.status >= 500) return 'Máy chủ đang gặp sự cố. Vui lòng thử lại sau.';
  return error.message || 'Không thể tạo tài khoản. Vui lòng thử lại.';
}

function validate({ fullName, username, password, confirmPassword }) {
  if (!fullName.trim()) return 'Vui lòng nhập họ và tên.';
  if (!USERNAME_PATTERN.test(username.trim())) {
    return 'Tên đăng nhập gồm 4–50 ký tự: chữ không dấu, số, dấu chấm, gạch dưới hoặc gạch ngang.';
  }
  if (!PASSWORD_RULES.every((rule) => rule.test(password))) return 'Mật khẩu phải có ít nhất 8 ký tự, gồm cả chữ và số.';
  if (password !== confirmPassword) return 'Mật khẩu nhập lại không khớp.';
  return '';
}

export default function RegisterPage() {
  const { isAuthenticated, isLoading, signUp } = useAuth();
  const [form, setForm] = useState({ fullName: '', username: '', password: '', confirmPassword: '' });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const location = useLocation();
  const destination = postAuthDestination(location.state?.from);

  if (isAuthenticated) return <Navigate to={destination} replace />;

  const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }));
  const describedBy = (id) => [id, error ? 'register-error' : null].filter(Boolean).join(' ') || undefined;

  async function handleSubmit(event) {
    event.preventDefault();
    if (isSubmitting || isLoading) return;
    const problem = validate(form);
    setError(problem);
    if (problem) return;
    setIsSubmitting(true);
    try {
      await signUp({ username: form.username.trim(), fullName: form.fullName.trim(), password: form.password });
    } catch (requestError) {
      setError(registerErrorMessage(requestError));
      setForm((current) => ({ ...current, password: '', confirmPassword: '' }));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="login-screen">
      <section className="login-card" aria-labelledby="register-title">
        <h1 id="register-title">Tạo tài khoản</h1>
        <p className="login-subtitle">Đăng ký để mua sắm, theo dõi đơn hàng và bảo hành tại {projectConfig.appName}.</p>
        <form className="login-form" onSubmit={handleSubmit} aria-busy={isSubmitting || isLoading} noValidate>
          <fieldset disabled={isSubmitting || isLoading}>
            <legend className="sr-only">Thông tin đăng ký</legend>
            <label className="sr-only" htmlFor="register-full-name">Họ và tên</label>
            <input id="register-full-name" name="fullName" type="text" autoComplete="name" maxLength={255}
              placeholder="Họ và tên" value={form.fullName} required onChange={update('fullName')}
              aria-describedby={describedBy()} />
            <label className="sr-only" htmlFor="register-username">Tên đăng nhập</label>
            <input id="register-username" name="username" type="text" autoComplete="username" autoCapitalize="none"
              spellCheck={false} maxLength={50} placeholder="Tên đăng nhập" value={form.username} required
              onChange={update('username')} aria-describedby={describedBy('register-username-hint')} />
            <p id="register-username-hint" className="register-hint">4–50 ký tự: chữ không dấu, số, dấu chấm, gạch dưới hoặc gạch ngang.</p>
            <label className="sr-only" htmlFor="register-password">Mật khẩu</label>
            <input id="register-password" name="password" type="password" autoComplete="new-password"
              placeholder="Mật khẩu" value={form.password} required onChange={update('password')}
              aria-describedby={describedBy('register-password-rules')} />
            <ul id="register-password-rules" className="register-rules" aria-label="Yêu cầu mật khẩu">
              {PASSWORD_RULES.map((rule) => {
                const met = rule.test(form.password);
                return <li key={rule.id} className={met ? 'is-met' : undefined}>
                  <span aria-hidden="true">{met ? '✓' : '•'}</span>{rule.label}
                  <span className="sr-only">{met ? ': đã đạt' : ': chưa đạt'}</span>
                </li>;
              })}
            </ul>
            <label className="sr-only" htmlFor="register-confirm-password">Nhập lại mật khẩu</label>
            <input id="register-confirm-password" name="confirmPassword" type="password" autoComplete="new-password"
              placeholder="Nhập lại mật khẩu" value={form.confirmPassword} required onChange={update('confirmPassword')}
              aria-describedby={describedBy()} />
            {error && <p id="register-error" className="auth-alert" role="alert">{error}</p>}
            <button className="login-submit" type="submit">
              {isLoading ? 'Đang kiểm tra phiên…' : isSubmitting ? 'Đang tạo tài khoản…' : 'Tạo tài khoản'}
            </button>
          </fieldset>
        </form>
        <p className="login-signup">Đã có tài khoản?{' '}
          <Link className="login-link" to="/login" state={location.state}>Đăng nhập</Link>
        </p>
        <p className="login-policy">Mật khẩu được mã hóa trên máy chủ.<br />Email chưa cần thiết ở bước đăng ký này.</p>
        <Link className="login-home-link" to="/">← Về trang chủ</Link>
      </section>
    </main>
  );
}
