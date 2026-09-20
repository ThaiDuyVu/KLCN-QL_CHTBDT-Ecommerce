import React from 'react';

const CategoryTable = ({ categories = [], onEdit, onDelete }) => {
    return (
        <table border="1" width="100%">
            <thead>
                <tr>
                    <th>Tên danh mục</th>
                    <th>Mô tả</th>
                    <th>ID Danh mục Cha</th>
                    <th>Trạng thái</th>
                    <th>Hành động</th>
                </tr>
            </thead>
            <tbody>
                {categories.map((cat) => (
                    <tr key={cat.categoryId}>
                        <td>{cat.categoryName}</td>
                        <td>{cat.description}</td>
                        <td>{cat.parentId ? cat.parentId : '--- (Danh mục gốc)'}</td>
                        <td>{cat.status}</td>
                        <td>
                            <button onClick={() => onEdit && onEdit(cat)}>Sửa</button>
                            <button onClick={() => onDelete && onDelete(cat)}>Xóa</button>
                        </td>
                    </tr>
                ))}
            </tbody>
        </table>
    );
};

export default CategoryTable;
