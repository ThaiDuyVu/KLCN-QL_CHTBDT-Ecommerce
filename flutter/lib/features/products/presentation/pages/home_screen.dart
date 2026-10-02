import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../presentation/providers/product_providers.dart';
import 'product_detail_screen.dart';
import 'package:ecommerce_app/features/warehouse/presentation/widgets/warehouse_selector_widget.dart';
import 'package:ecommerce_app/features/cart/presentation/pages/cart_screen.dart';
import 'package:ecommerce_app/features/profile/presentation/pages/profile_screen.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final productsAsync = ref.watch(productListProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Ecommerce'),
        actions: [
          IconButton(
            icon: const Icon(Icons.shopping_cart),
            onPressed: () {
              // Chuyển sang màn hình Giỏ Hàng
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => const CartScreen()),
              );
            },
          ),
          IconButton(
            icon: const Icon(Icons.person),
            onPressed: () {
              // Chuyển sang màn hình Cá nhân
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => const ProfileScreen()),
              );
            },
          ),
        ],
      ),
      // 2. BỌC TRONG SAFEAREA VÀ COLUMN
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Tiêu đề chọn chi nhánh
            const Padding(
              padding: EdgeInsets.all(16.0),
              child: Text(
                'Vui lòng chọn chi nhánh:', 
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)
              ),
            ),
            
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 16.0),
              child: WarehouseSelectorWidget(),
            ),
            
            const SizedBox(height: 16),
            
            // Tiêu đề danh sách sản phẩm
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 16.0),
              child: Text(
                'Sản phẩm nổi bật:', 
                style: TextStyle(fontSize: 16, color: Colors.grey)
              ),
            ),
            
            // 4. HIỂN THỊ DANH SÁCH SẢN PHẨM (Bọc Expanded để GridView không bị lỗi tràn màn hình)
            Expanded(
              child: Padding(
                padding: const EdgeInsets.all(8.0),
                child: productsAsync.when(
                  data: (list) {
                    if (list.isEmpty) {
                      return const Center(child: Text('Không có sản phẩm nào tại chi nhánh này'));
                    }
                    return GridView.builder(
                      itemCount: list.length,
                      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: 2, 
                        childAspectRatio: 0.68,
                        mainAxisSpacing: 8,
                        crossAxisSpacing: 8,
                      ),
                      itemBuilder: (ctx, idx) {
                        final p = list[idx];
                        final available = p.availableQuantity;
                        return GestureDetector(
                          onTap: available > 0 
                              ? () => Navigator.push(
                                    context, 
                                    MaterialPageRoute(builder: (_) => ProductDetailScreen(product: p))
                                  ) 
                              : null,
                          child: Card(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Expanded(
                                  child: p.imageUrl != null ? Image.network(p.imageUrl!, fit: BoxFit.cover, width: double.infinity)
                                      : Container(color: Colors.grey[200]),
                                ),
                                Padding(
                                  padding: const EdgeInsets.all(8.0),
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(p.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                                      Text(p.brand, style: const TextStyle(fontSize: 12, color: Colors.grey)),

                                      const SizedBox(height: 4),
                                      Text('${p.price} đ', style: const TextStyle(fontWeight: FontWeight.bold)),
                                      Text(
                                        available > 0 ? 'Còn $available sản phẩm' : 'Hết hàng', 
                                        style: TextStyle(color: available > 0 ? Colors.green : Colors.red)
                                      ),
                                    ],
                                  ),
                                )
                              ],
                            ),
                          ),
                        );
                      },
                    );
                  },
                  loading: () => const Center(child: CircularProgressIndicator()),
                  error: (e, st) => const Center(child: Text('Lỗi tải danh sách sản phẩm')),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
