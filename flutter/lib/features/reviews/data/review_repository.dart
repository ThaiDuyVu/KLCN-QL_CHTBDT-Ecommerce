import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ecommerce_app/core/network/api_client.dart';

/// A customer review (backend `ReviewResponse`).
class ProductReview {
  final String id;
  final String orderItemId;
  final String customerName;
  final int rating;
  final String? text;
  final String? reply;
  final String? createdAt;

  const ProductReview({
    required this.id,
    required this.orderItemId,
    required this.customerName,
    required this.rating,
    this.text,
    this.reply,
    this.createdAt,
  });

  factory ProductReview.fromJson(Map<String, dynamic> json) => ProductReview(
        id: (json['reviewId'] ?? '').toString(),
        orderItemId: (json['orderItemId'] ?? '').toString(),
        customerName: (json['customerName'] ?? '').toString(),
        rating: ((json['rating'] ?? 0) as num).toInt(),
        text: json['reviewText']?.toString(),
        reply: json['replyText']?.toString(),
        createdAt: json['createdAt']?.toString(),
      );
}

/// Visible reviews of a product with the average rating.
class ProductReviewPage {
  final double? averageRating;
  final int totalReviews;
  final List<ProductReview> reviews;

  const ProductReviewPage({this.averageRating, required this.totalReviews, required this.reviews});

  factory ProductReviewPage.fromJson(Map<String, dynamic> json) => ProductReviewPage(
        averageRating: (json['averageRating'] as num?)?.toDouble(),
        totalReviews: ((json['totalReviews'] ?? 0) as num).toInt(),
        reviews: ((json['content'] ?? const <dynamic>[]) as List)
            .map((e) => ProductReview.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
      );
}

class ReviewRepository {
  final Dio client = ApiClient.dio;

  Future<ProductReviewPage> forProduct(String productId) async {
    final resp = await client.get('/api/v1/products/$productId/reviews', queryParameters: {'page': 0, 'size': 10});
    return ProductReviewPage.fromJson(Map<String, dynamic>.from(resp.data as Map));
  }

  Future<List<ProductReview>> mineForOrder(String orderId) async {
    final resp = await client.get('/api/reviews/mine/by-order/$orderId');
    final data = resp.data;
    if (data is! List) return [];
    return data.map((e) => ProductReview.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<void> create(String orderItemId, int rating, String text) async {
    await client.post('/api/reviews', data: {
      'orderItemId': orderItemId,
      'rating': rating,
      if (text.trim().isNotEmpty) 'reviewText': text.trim(),
    });
  }
}

final reviewRepositoryProvider = Provider((ref) => ReviewRepository());

final productReviewsProvider = FutureProvider.family<ProductReviewPage, String>((ref, productId) {
  return ref.read(reviewRepositoryProvider).forProduct(productId);
});

final orderReviewsProvider = FutureProvider.family<List<ProductReview>, String>((ref, orderId) {
  return ref.read(reviewRepositoryProvider).mineForOrder(orderId);
});
