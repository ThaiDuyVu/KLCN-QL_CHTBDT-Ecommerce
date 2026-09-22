import axios from 'axios';

const API_URL = 'http://localhost:8080/api/v1/categories';

export const categoryApi = {
    getAllCategories: async () => {
        const response = await axios.get(API_URL);
        return response.data;
    },

    getRootCategories: async () => {
        const response = await axios.get(`${API_URL}/roots`);
        return response.data;
    },

    getCategoryChildren: async (parentId) => {
        const response = await axios.get(`${API_URL}/${parentId}/children`);
        return response.data;
    },

    createCategory: async (data) => {
        const response = await axios.post(API_URL, data);
        return response.data;
    },

    updateCategory: async (id, data) => {
        const response = await axios.put(`${API_URL}/${id}`, data);
        return response.data;
    },

    deleteCategory: async (id) => {
        const response = await axios.delete(`${API_URL}/${id}`);
        return response.data;
    }
};
