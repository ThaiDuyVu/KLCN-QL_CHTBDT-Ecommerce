import React, { useState, useEffect } from 'react';
import { categoryApi } from '../api/categoryApi';

const CategoryForm = ({ initialData = null, categories = [], onSuccess, onCancel }) => {
    const [categoryName, setCategoryName] = useState(initialData?.categoryName || '');
    const [description, setDescription] = useState(initialData?.description || '');
    const [parentId, setParentId] = useState(initialData?.parentId || null);
    const [status, setStatus] = useState(initialData?.status || 'ACTIVE');
    const [isSubmitting, setIsSubmitting] = useState(false);

    useEffect(() => {
        setCategoryName(initialData?.categoryName || '');
        setDescription(initialData?.description || '');
        setParentId(initialData?.parentId || null);
        setStatus(initialData?.status || 'ACTIVE');
    }, [initialData]);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsSubmitting(true);
        try {
            const payload = { categoryName, description, parentId, status };
            if (initialData?.categoryId) {
                await categoryApi.updateCategory(initialData.categoryId, payload);
            } else {
                await categoryApi.createCategory(payload);
            }
            onSuccess && onSuccess();
        } catch (err) {
            const msg = err.response?.data?.message || 'Lỗi khi lưu danh mục';
            alert(msg);
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <form onSubmit={handleSubmit}>
            <div>
                <label>Tên danh mục</label>
                <input value={categoryName} onChange={(e) => setCategoryName(e.target.value)} required />
            </div>
            <div>
                <label>Mô tả</label>
                <input value={description} onChange={(e) => setDescription(e.target.value)} />
            </div>
            <div>
                <label>Danh mục cha</label>
                <select value={parentId || ''} onChange={(e) => setParentId(e.target.value || null)}>
                    <option value="">-- Không chọn (Root) --</option>
                    {categories.map((c) => (
                        <option key={c.categoryId} value={c.categoryId}>
                            {c.categoryName}
                        </option>
                    ))}
                </select>
            </div>
            <div>
                <label>Trạng thái</label>
                <select value={status} onChange={(e) => setStatus(e.target.value)}>
                    <option value="ACTIVE">ACTIVE</option>
                    <option value="INACTIVE">INACTIVE</option>
                </select>
            </div>

            <div>
                <button type="submit" disabled={isSubmitting}>{isSubmitting ? 'Đang gửi...' : 'Lưu'}</button>
                <button type="button" onClick={onCancel} disabled={isSubmitting}>Hủy</button>
            </div>
        </form>
    );
};

export default CategoryForm;
