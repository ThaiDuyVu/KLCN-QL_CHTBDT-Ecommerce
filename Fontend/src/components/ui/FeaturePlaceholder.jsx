import PageHeader from './PageHeader';

export default function FeaturePlaceholder({ title, description, plannedSections }) {
  return (
    <section>
      <PageHeader eyebrow="Không gian nghiệp vụ" title={title} description={description} />
      <div className="panel empty-state">
        <span className="badge">Skeleton · Chưa có dữ liệu</span>
        <h2>Sẵn sàng phát triển tính năng</h2>
        <p className="muted">Trang này chưa thực hiện CRUD, xử lý nghiệp vụ hoặc gọi backend.</p>
        <ul className="planned-list">
          {plannedSections.map((section) => <li key={section}>{section}</li>)}
        </ul>
      </div>
    </section>
  );
}
