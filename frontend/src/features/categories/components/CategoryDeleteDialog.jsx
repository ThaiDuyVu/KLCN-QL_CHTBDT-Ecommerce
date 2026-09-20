import React from 'react';

// window delete for category 
const CategoryDeleteDialog = ({ category, onConfirm, onCancel }) => {
    if (!category) return null;

    const handleConfirm = () => {
        onConfirm && onConfirm(category);
    };

    return (
        <div style={{ border: '1px solid #ccc', padding: 12, background: '#fff' }}>
            <p>Xác nhận xóa danh mục "{category.categoryName}"?</p>
            <button onClick={handleConfirm}>Xóa</button>
            <button onClick={onCancel}>Hủy</button>
        </div>
    );
};

export default CategoryDeleteDialog;
