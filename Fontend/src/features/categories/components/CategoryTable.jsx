export default function CategoryTable({ categories, categoryNames, canManage, onEdit, onDelete }) {
  return (
    <div className="panel category-table-wrap">
      <table className="category-table">
        <thead><tr><th>Tên danh mục</th><th>Danh mục cha</th><th>Mô tả</th><th>Trạng thái</th>{canManage && <th>Thao tác</th>}</tr></thead>
        <tbody>{categories.map((category) => (
          <tr key={category.categoryId}>
            <th scope="row">{category.categoryName}</th>
            <td>{category.parentId ? categoryNames.get(category.parentId) || 'Không tìm thấy danh mục cha' : 'Danh mục gốc'}</td>
            <td>{category.description || '—'}</td>
            <td><span className={`category-status category-status-${category.status.toLowerCase()}`}>
              {category.status === 'ACTIVE' ? 'Đang hoạt động' : 'Ngừng hoạt động'}
            </span></td>
            {canManage && <td><div className="category-row-actions">
              <button className="button button-quiet" type="button" onClick={() => onEdit(category)}>Sửa</button>
              <button className="button category-delete-button" type="button" onClick={() => onDelete(category)}>Xóa</button>
            </div></td>}
          </tr>
        ))}</tbody>
      </table>
    </div>
  );
}
