import React from 'react';

// Hiển thị cây danh mục với kiểm tra chu trình và độ sâu.
const MAX_DEPTH = 12;

const buildMap = (items) => {
    const map = {};
    items.forEach(i => { map[i.categoryId] = { ...i, children: [] }; });
    return map;
};

const attachChildren = (map) => {
    const roots = [];
    Object.values(map).forEach(node => {
        if (node.parentId) {
            const parent = map[node.parentId];
            if (parent) parent.children.push(node);
            else roots.push(node); // mồ côi -> coi như root
        } else {
            roots.push(node);
        }
    });
    return roots;
};

const Node = ({ node, visited = new Set(), depth = 0 }) => {
    if (depth > MAX_DEPTH) {
        return <li style={{ color: 'red' }}>Depth limit reached - possible cycle in data</li>;
    }
    if (visited.has(node.categoryId)) {
        return <li style={{ color: 'red' }}>Cycle detected at {node.categoryName}</li>;
    }

    const nextVisited = new Set(visited);
    nextVisited.add(node.categoryId);

    return (
        <li>
            {node.categoryName}
            {node.children && node.children.length > 0 && (
                <ul>
                    {node.children.map(c => (
                        <Node key={c.categoryId} node={c} visited={nextVisited} depth={depth + 1} />
                    ))}
                </ul>
            )}
        </li>
    );
};

const CategoryTree = ({ categories = [] }) => {
    if (!categories || categories.length === 0) return <div>Không có danh mục để tạo cây.</div>;

    const map = buildMap(categories);
    const roots = attachChildren(map);

    return (
        <div>
            <h3>Cây danh mục</h3>
            <ul>
                {roots.map(r => <Node key={r.categoryId} node={r} />)}
            </ul>
        </div>
    );
};

export default CategoryTree;
