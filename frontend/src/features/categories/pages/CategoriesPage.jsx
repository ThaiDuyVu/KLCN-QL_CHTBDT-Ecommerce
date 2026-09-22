import React, { useState, useEffect } from 'react';
import { categoryApi } from '../api/categoryApi';
import CategoryTable from '../components/CategoryTable';
import CategoryForm from '../components/CategoryForm';
import CategoryTree from '../components/CategoryTree';

const CategoriesPage = () => {
    const [categories, setCategories] = useState([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);
    const [showForm, setShowForm] = useState(false);
    const [editing, setEditing] = useState(null);

    const fetchCategories = async () => {
        setIsLoading(true);
        setError(null);
        try {
            const data = await categoryApi.getAllCategories();
            setCategories(data);
        } catch (err) {
            setError('Không thể tải danh mục. Vui lòng thử lại.');
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        fetchCategories();
    }, []);

    const handleDelete = async (cat) => {
        if (!window.confirm(`Xác nhận xóa danh mục "${cat.categoryName}"?`)) return;
        try {
            await categoryApi.deleteCategory(cat.categoryId);
            alert('Xóa thành công');
            fetchCategories();
        } catch (err) {
            const errorMsg = err.response?.data?.message || 'Không thể xóa danh mục này.';
            alert(errorMsg);
        }
    };

    const handleEdit = (cat) => {
        setEditing(cat);
        setShowForm(true);
    };

    const handleFormSuccess = () => {
        setShowForm(false);
        setEditing(null);
        fetchCategories();
    };

    if (isLoading) return <div>Đang tải danh mục...</div>;
    if (error) return <div style={{ color: 'red' }}>{error}</div>;

    return (
        <div>
            <h2>Quản lý Danh mục</h2>
            <button onClick={() => { setEditing(null); setShowForm(true); }}>Tạo danh mục mới</button>

            {categories.length === 0 ? (
                <p>Chưa có danh mục nào.</p>
            ) : (
                <>
                    <CategoryTree categories={categories} />
                    <CategoryTable categories={categories} onEdit={handleEdit} onDelete={handleDelete} />
                </>
            )}

            {showForm && (
                <div style={{ border: '1px solid #ddd', padding: 12, marginTop: 12 }}>
                    <h3>{editing ? 'Sửa danh mục' : 'Tạo danh mục mới'}</h3>
                    <CategoryForm initialData={editing} categories={categories} onSuccess={handleFormSuccess} onCancel={() => { setShowForm(false); setEditing(null); }} />
                </div>
            )}
        </div>
    );
};

export default CategoriesPage;
