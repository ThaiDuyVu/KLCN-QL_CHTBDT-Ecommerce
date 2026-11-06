import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_error.dart';
import '../../../core/utils/custom_snackbar.dart';
import '../../orders/data/models/order_model.dart';
import '../data/review_repository.dart';

String stars(int rating) => '★' * rating + '☆' * (5 - rating);

/// Reviews block on the product detail screen.
class ProductReviewsSection extends ConsumerWidget {
  final String productId;

  const ProductReviewsSection({super.key, required this.productId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final reviews = ref.watch(productReviewsProvider(productId));
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('Đánh giá sản phẩm', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
        const SizedBox(height: 12),
        reviews.when(
          loading: () => const LinearProgressIndicator(),
          error: (e, _) => const Text('Chưa tải được đánh giá.'),
          data: (page) => page.totalReviews == 0
              ? const Text('Chưa có đánh giá. Bạn có thể đánh giá sau khi nhận hàng, từ chi tiết đơn hàng.',
                  style: TextStyle(color: Colors.blueGrey))
              : Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('${page.averageRating?.toStringAsFixed(1) ?? '-'}/5  ${stars((page.averageRating ?? 0).round())}  · ${page.totalReviews} đánh giá',
                        style: const TextStyle(fontWeight: FontWeight.w600, color: Color(0xFFE8A317))),
                    ...page.reviews.map((r) => Padding(
                          padding: const EdgeInsets.only(top: 12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('${r.customerName}  ${stars(r.rating)}', style: const TextStyle(fontWeight: FontWeight.w600)),
                              if ((r.text ?? '').isNotEmpty) Text(r.text!),
                              if ((r.reply ?? '').isNotEmpty)
                                Container(
                                  margin: const EdgeInsets.only(top: 6),
                                  padding: const EdgeInsets.all(8),
                                  color: const Color(0xFFF3F7F4),
                                  child: Text('Cửa hàng phản hồi: ${r.reply}'),
                                ),
                            ],
                          ),
                        )),
                  ],
                ),
        ),
      ],
    );
  }
}

/// Rate each line of a delivered order once.
class OrderReviewsSection extends ConsumerWidget {
  final Order order;

  const OrderReviewsSection({super.key, required this.order});

  Future<void> _write(BuildContext context, WidgetRef ref, OrderItem item) async {
    int rating = 5;
    final text = TextEditingController();
    final submitted = await showDialog<bool>(
      context: context,
      builder: (dctx) => StatefulBuilder(
        builder: (dctx, setState) => AlertDialog(
          title: Text('Đánh giá ${item.productName}'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(5, (i) => IconButton(
                      icon: Icon(i < rating ? Icons.star : Icons.star_border, color: const Color(0xFFE8A317)),
                      onPressed: () => setState(() => rating = i + 1),
                    )),
              ),
              TextField(controller: text, maxLength: 2000, maxLines: 3, decoration: const InputDecoration(labelText: 'Nhận xét')),
            ],
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(dctx, false), child: const Text('Hủy')),
            ElevatedButton(onPressed: () => Navigator.pop(dctx, true), child: const Text('Gửi đánh giá')),
          ],
        ),
      ),
    );
    if (submitted != true) return;
    try {
      await ref.read(reviewRepositoryProvider).create(item.id, rating, text.text);
      ref.invalidate(orderReviewsProvider(order.id));
      if (context.mounted) CustomSnackbar.showSuccess(context, 'Cảm ơn bạn đã đánh giá');
    } on DioException catch (e) {
      if (context.mounted) CustomSnackbar.showError(context, apiErrorMessage(e));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final reviewed = ref.watch(orderReviewsProvider(order.id));
    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('ĐÁNH GIÁ SẢN PHẨM', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Colors.deepOrange)),
            const Divider(height: 20),
            ...order.items.map((item) {
              final mine = reviewed.valueOrNull?.where((r) => r.orderItemId == item.id).firstOrNull;
              return Row(
                children: [
                  Expanded(child: Text(item.productName, style: const TextStyle(fontSize: 13))),
                  mine != null
                      ? Text(stars(mine.rating), style: const TextStyle(color: Color(0xFFE8A317)))
                      : TextButton(
                          onPressed: reviewed.hasValue ? () => _write(context, ref, item) : null,
                          child: const Text('Đánh giá'),
                        ),
                ],
              );
            }),
          ],
        ),
      ),
    );
  }
}
