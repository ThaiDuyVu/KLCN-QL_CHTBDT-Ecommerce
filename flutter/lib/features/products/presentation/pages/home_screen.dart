import 'package:ecommerce_app/features/cart/presentation/providers/cart_provider.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../../warehouse/presentation/providers/warehouse_providers.dart';
import '../../data/models/product_model.dart';
import '../providers/product_providers.dart';
import '../widgets/shop_widgets.dart';
import 'product_detail_screen.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});
  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  String _keyword = '', _category = 'Tất cả', _sort = 'Đề xuất';
  bool _inStock = false;
  final _search = TextEditingController();
  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final products = ref.watch(productListProvider);
    final warehouse = ref.watch(selectedWarehouseProvider);
    final customer = ref.watch(authProvider).user?['roleName'] == 'CUSTOMER';
    return Scaffold(
      backgroundColor: const Color(0xfff7fafb),
      appBar: AppBar(
        title: const Row(
          children: [
            Icon(Icons.devices, color: shopBlue),
            SizedBox(width: 10),
            Text('Điện Việt', style: TextStyle(fontWeight: FontWeight.w800)),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Tải lại sản phẩm',
            onPressed: () => ref.invalidate(productListProvider),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(productListProvider);
          await ref.read(productListProvider.future);
        },
        child: CustomScrollView(
          slivers: [
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (!customer)
                      const Padding(
                        padding: EdgeInsets.only(bottom: 12),
                        child: Text(
                          'Chế độ nhân viên · Tra cứu sản phẩm. Quản trị trên web.',
                          style: TextStyle(color: shopBlue),
                        ),
                      ),
                    TextField(
                      controller: _search,
                      onChanged: (value) => setState(() => _keyword = value),
                      decoration: InputDecoration(
                        hintText: 'Tìm sản phẩm, thương hiệu…',
                        prefixIcon: const Icon(Icons.search),
                        suffixIcon: _keyword.isEmpty
                            ? null
                            : IconButton(
                                tooltip: 'Xóa tìm kiếm',
                                onPressed: () {
                                  _search.clear();
                                  setState(() => _keyword = '');
                                },
                                icon: const Icon(Icons.close),
                              ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    ref.watch(warehouseListProvider).when(
  loading: () => const LinearProgressIndicator(),
  error: (_, _) => TextButton(
    onPressed: () => ref.invalidate(warehouseListProvider),
    child: const Text('Không tải được chi nhánh · Thử lại'),
  ),
  data: (list) {
    // 1. FIX LẶP CHI NHÁNH: Lọc trùng theo TÊN thay vì ID. 
    // Nó sẽ giữ lại kho gốc đầu tiên (kho có chứa dữ liệu tồn kho thật).
    final seenNames = <String>{};
    final uniqueWarehouses = list.where((w) => seenNames.add(w.name)).toList();

    // 2. FIX HẾT HÀNG: Tự động chọn kho đầu tiên nếu chưa có kho nào được chọn.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (warehouse == null && uniqueWarehouses.isNotEmpty) {
        ref.read(selectedWarehouseProvider.notifier).select(uniqueWarehouses.first);
      }
    });

    return DropdownButtonFormField<String?>(
      value: warehouse?.id, // Liên kết trực tiếp với state hiện tại
      decoration: const InputDecoration(
        prefixIcon: Icon(Icons.location_on_outlined),
        labelText: 'Chi nhánh xem hàng',
      ),
      items: uniqueWarehouses.map((w) => DropdownMenuItem<String?>(
        value: w.id,
        child: Text(w.name, overflow: TextOverflow.ellipsis),
      )).toList(),
      onChanged: (newId) async {
        if (newId == null || newId == warehouse?.id) return;

        final newWarehouse = uniqueWarehouses.firstWhere((w) => w.id == newId);

        // 3. CHẶN CHUYỂN KHO KHI CÓ GIỎ HÀNG (Phase 4)
        final cartList = ref.read(cartListProvider).valueOrNull;
        if (cartList != null && cartList.isNotEmpty && warehouse != null) {
          final should = await showDialog<bool>(
            context: context,
            builder: (dctx) => AlertDialog(
              title: const Text('Thay đổi chi nhánh'),
              content: const Text('Đổi chi nhánh sẽ xóa giỏ hàng hiện tại. Bạn có muốn tiếp tục?'),
              actions: [
                TextButton(onPressed: () => Navigator.pop(dctx, false), child: const Text('Hủy')),
                TextButton(onPressed: () => Navigator.pop(dctx, true), child: const Text('Tiếp tục')),
              ],
            ),
          );

          if (should != true) return;

          // Thực thi API xóa giỏ hàng
          await ref.read(cartRepositoryProvider).selectWarehouse(newWarehouse.id, clearItems: true);
          ref.invalidate(cartListProvider);
          ref.invalidate(cartProvider);
        }

        // Cập nhật state
        ref.read(selectedWarehouseProvider.notifier).select(newWarehouse);
      },
    );
  },
),
                    const SizedBox(height: 18),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(22),
                      decoration: BoxDecoration(
                        color: shopTint,
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: const Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'CÔNG NGHỆ CHO MỖI NGÀY',
                            style: TextStyle(
                              color: shopBlue,
                              fontSize: 11,
                              letterSpacing: 1.5,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          SizedBox(height: 10),
                          Text(
                            'Tìm thiết bị\nphù hợp với bạn.',
                            style: TextStyle(
                              color: shopInk,
                              fontSize: 28,
                              height: 1.2,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                          SizedBox(height: 10),
                          Text(
                            'Khám phá điện thoại, laptop và thiết bị công nghệ.',
                            style: TextStyle(color: shopBlue),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 22),
                    const Text(
                      'Khám phá sản phẩm',
                      style: TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 12),
                    products.when(
                      data: (list) {
                        final categories = [
                          'Tất cả',
                          ...list
                              .map((p) => p.category)
                              .where((c) => c.isNotEmpty)
                              .toSet(),
                        ];
                        return SingleChildScrollView(
                          scrollDirection: Axis.horizontal,
                          child: Row(
                            children: categories
                                .map(
                                  (c) => Padding(
                                    padding: const EdgeInsets.only(right: 8),
                                    child: ChoiceChip(
                                      label: Text(c),
                                      selected: _category == c,
                                      onSelected: (_) =>
                                          setState(() => _category = c),
                                    ),
                                  ),
                                )
                                .toList(),
                          ),
                        );
                      },
                      loading: () => const SizedBox.shrink(),
                      error: (_, _) => const SizedBox.shrink(),
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        Expanded(
                          child: FilterChip(
                            label: const Text('Còn hàng'),
                            selected: _inStock,
                            onSelected: warehouse == null
                                ? null
                                : (v) => setState(() => _inStock = v),
                          ),
                        ),
                        DropdownButton<String>(
                          value: _sort,
                          underline: const SizedBox.shrink(),
                          items: ['Đề xuất', 'Giá tăng dần', 'Giá giảm dần']
                              .map(
                                (s) =>
                                    DropdownMenuItem(value: s, child: Text(s)),
                              )
                              .toList(),
                          onChanged: (s) => setState(() => _sort = s!),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            ...products.when(
              data: (list) {
                final filtered = list
                    .where(
                      (p) =>
                          (_category == 'Tất cả' || p.category == _category) &&
                          (!_inStock ||
                              warehouse == null ||
                              p.availableQuantity > 0) &&
                          '${p.name} ${p.brand}'.toLowerCase().contains(
                            _keyword.trim().toLowerCase(),
                          ),
                    )
                    .toList();
                if (_sort != 'Đề xuất')
                  filtered.sort((a, b) {
                    if (a.price == null) return b.price == null ? 0 : 1;
                    if (b.price == null) return -1;
                    return _sort == 'Giá tăng dần'
                        ? a.price!.compareTo(b.price!)
                        : b.price!.compareTo(a.price!);
                  });
                if (filtered.isEmpty)
                  return <Widget>[
                    const SliverToBoxAdapter(
                      child: ShopMessage(
                        icon: Icons.search_off,
                        title: 'Chưa tìm thấy sản phẩm',
                        message: 'Thử từ khóa, danh mục hoặc chi nhánh khác.',
                      ),
                    ),
                  ];
                return <Widget>[
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                      child: Text(
                        '${filtered.length} sản phẩm · ${warehouse?.name ?? 'Chọn chi nhánh để xem tồn kho'}',
                        style: const TextStyle(
                          color: Colors.blueGrey,
                          fontSize: 12,
                        ),
                      ),
                    ),
                  ),
                  SliverPadding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                    sliver: SliverLayoutBuilder(
                      builder: (context, constraints) => SliverGrid(
                        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: constraints.crossAxisExtent >= 700
                              ? 4
                              : constraints.crossAxisExtent >= 500
                              ? 3
                              : 2,
                          mainAxisExtent:
                              330 +
                              (MediaQuery.textScalerOf(context).scale(14) -
                                      14) *
                                  8,
                          crossAxisSpacing: 12,
                          mainAxisSpacing: 12,
                        ),
                        delegate: SliverChildBuilderDelegate(
                          (context, i) => _ProductCard(
                            product: filtered[i],
                            hasWarehouse: warehouse != null,
                          ),
                          childCount: filtered.length,
                        ),
                      ),
                    ),
                  ),
                ];
              },
              loading: () => <Widget>[
                const SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.all(48),
                    child: Center(child: CircularProgressIndicator()),
                  ),
                ),
              ],
              error: (_, _) => <Widget>[
                SliverToBoxAdapter(
                  child: ShopMessage(
                    icon: Icons.wifi_off,
                    title: 'Chưa tải được sản phẩm',
                    message: 'Kiểm tra kết nối rồi thử lại.',
                    retry: () => ref.invalidate(productListProvider),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ProductCard extends StatelessWidget {
  final Product product;
  final bool hasWarehouse;
  const _ProductCard({required this.product, required this.hasWarehouse});
  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(12),
      side: const BorderSide(color: Color(0xffe0e6eb)),
    ),
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => ProductDetailScreen(product: product),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Center(
                child: Hero(
                  tag: 'product-${product.id}',
                  child: ProductImage(product: product),
                ),
              ),
            ),
            const SizedBox(height: 12),
            Text(
              product.brand.toUpperCase(),
              maxLines: 1,
              style: const TextStyle(
                color: Colors.blueGrey,
                fontSize: 10,
                letterSpacing: .7,
              ),
            ),
            const SizedBox(height: 6),
            SizedBox(
              height: 42,
              child: Text(
                product.name,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontWeight: FontWeight.w700,
                  height: 1.3,
                ),
              ),
            ),
            const SizedBox(height: 8),
            ProductPrice(product: product),
            const SizedBox(height: 8),
            Text(
              hasWarehouse
                  ? product.availableQuantity > 0
                        ? '● Còn ${product.availableQuantity} sản phẩm'
                        : '● Hết hàng'
                  : 'Chọn chi nhánh xem tồn kho',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 11,
                color: !hasWarehouse
                    ? Colors.blueGrey
                    : product.availableQuantity > 0
                    ? Colors.teal
                    : shopOrange,
              ),
            ),
            const SizedBox(height: 10),
            const Text(
              'Xem chi tiết →',
              style: TextStyle(
                color: shopBlue,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
