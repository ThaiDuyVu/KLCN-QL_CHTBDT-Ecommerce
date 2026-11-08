import { Component } from 'react';

// Keeps the header and navigation usable when one page fails to load or render,
// e.g. a stale tab requesting a chunk removed by a redeploy, instead of a blank screen.
export default class RouteErrorBoundary extends Component {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return <section className="panel" role="alert">
      <h2>Không tải được trang này</h2>
      <p>Hệ thống có thể vừa được cập nhật. Vui lòng tải lại trang để dùng phiên bản mới nhất.</p>
      <button type="button" className="button" onClick={() => window.location.reload()}>Tải lại trang</button>
    </section>;
  }
}
