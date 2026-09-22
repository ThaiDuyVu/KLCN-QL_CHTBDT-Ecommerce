function buildTree(categories) {
  const nodes = new Map(categories.map((category) => [category.categoryId, { ...category, children: [] }]));
  const roots = [];
  nodes.forEach((node) => {
    const parent = node.parentId ? nodes.get(node.parentId) : null;
    if (parent && parent.categoryId !== node.categoryId) parent.children.push(node);
    else roots.push(node);
  });
  const sort = (items) => {
    items.sort((left, right) => left.categoryName.localeCompare(right.categoryName, 'vi'));
    items.forEach((item) => sort(item.children));
  };
  sort(roots);
  return { roots, nodes };
}

function TreeNode({ node, path }) {
  if (path.has(node.categoryId)) {
    return <li className="category-tree-warning">Dữ liệu có chu trình tại {node.categoryName}</li>;
  }
  const nextPath = new Set(path);
  nextPath.add(node.categoryId);
  return (
    <li>
      <span>{node.categoryName}</span>
      <small>{node.status === 'ACTIVE' ? 'Đang hoạt động' : 'Ngừng hoạt động'}</small>
      {node.children.length > 0 && <ul>{node.children.map((child) => (
        <TreeNode key={child.categoryId} node={child} path={nextPath} />
      ))}</ul>}
    </li>
  );
}

export default function CategoryTree({ categories }) {
  const { roots, nodes } = buildTree(categories);
  const reachable = new Set();
  const visit = (node) => {
    if (reachable.has(node.categoryId)) return;
    reachable.add(node.categoryId);
    node.children.forEach(visit);
  };
  roots.forEach(visit);
  const detached = [...nodes.values()].filter((node) => !reachable.has(node.categoryId));
  const visibleRoots = [...roots, ...detached];

  return (
    <section className="panel category-tree" aria-labelledby="category-tree-title">
      <h2 id="category-tree-title">Cây danh mục</h2>
      <ul>{visibleRoots.map((root) => <TreeNode key={root.categoryId} node={root} path={new Set()} />)}</ul>
    </section>
  );
}
