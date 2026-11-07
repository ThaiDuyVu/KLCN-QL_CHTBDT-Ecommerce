import 'dart:math';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ecommerce_app/core/network/api_client.dart';

/// Product suggested by the assistant (chatbot `ProductCard`, snake_case JSON).
class ChatProduct {
  final String productId;
  final String productName;
  final double? effectivePrice;
  final double? originalPrice;

  const ChatProduct({required this.productId, required this.productName, this.effectivePrice, this.originalPrice});

  factory ChatProduct.fromJson(Map<String, dynamic> json) => ChatProduct(
        productId: (json['product_id'] ?? '').toString(),
        productName: (json['product_name'] ?? '').toString(),
        effectivePrice: num.tryParse('${json['effective_price'] ?? ''}')?.toDouble(),
        originalPrice: num.tryParse('${json['original_price'] ?? ''}')?.toDouble(),
      );
}

class ChatReply {
  final String message;
  final List<ChatProduct> products;
  final List<String> suggestions;

  const ChatReply({required this.message, this.products = const [], this.suggestions = const []});

  factory ChatReply.fromJson(Map<String, dynamic> json) => ChatReply(
        message: (json['message'] ?? '').toString(),
        products: ((json['products'] ?? const <dynamic>[]) as List)
            .map((e) => ChatProduct.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
        suggestions: ((json['suggested_questions'] ?? const <dynamic>[]) as List).map((e) => e.toString()).toList(),
      );
}

/// Same chatbot service as the web storefront (`/api/v1/chat`, routed by the gateway to the chatbot service).
class ChatRepository {
  /// The assistant may call the LLM and product search; the web allows up to 180 s as well.
  static const Duration replyTimeout = Duration(seconds: 180);
  static const int maxMessageLength = 4000;
  final Dio client = ApiClient.dio;

  /// A new conversation id; the first message creates the session on the server.
  static String newSessionId() {
    final random = Random.secure();
    final bytes = List<int>.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
  }

  Future<ChatReply> send(String sessionId, String message) async {
    final resp = await client.post(
      '/api/v1/chat/sessions/$sessionId/messages',
      data: {'message': message},
      options: Options(receiveTimeout: replyTimeout, sendTimeout: const Duration(seconds: 30)),
    );
    return ChatReply.fromJson(Map<String, dynamic>.from(resp.data as Map));
  }
}

final chatRepositoryProvider = Provider((ref) => ChatRepository());
