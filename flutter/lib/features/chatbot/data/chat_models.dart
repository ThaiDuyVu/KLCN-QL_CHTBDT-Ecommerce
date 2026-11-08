// Chatbot service contracts (snake_case JSON), same shapes the web storefront reads.

double? _money(Object? value) => value == null ? null : num.tryParse('$value')?.toDouble();
int? _int(Object? value) => value == null ? null : num.tryParse('$value')?.toInt();
List<Map<String, dynamic>> _maps(Object? value) =>
    value is List ? value.whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList() : const [];
List<String> _strings(Object? value) => value is List ? value.map((e) => e.toString()).toList() : const [];
DateTime? _date(Object? value) => value == null ? null : DateTime.tryParse('$value')?.toLocal();

/// Product suggested by the assistant (`ProductCard`).
class ChatProduct {
  final String productId, productName;
  final String? variantId, sku, ram, storage, color, imageUrl;
  final double? originalPrice, effectivePrice;
  /// null = stock could not be determined; never shown as sold out.
  final int? availableQuantity;
  final int? warrantyMonths;
  final List<String> reasons;

  const ChatProduct({
    required this.productId,
    required this.productName,
    this.variantId,
    this.sku,
    this.ram,
    this.storage,
    this.color,
    this.imageUrl,
    this.originalPrice,
    this.effectivePrice,
    this.availableQuantity,
    this.warrantyMonths,
    this.reasons = const [],
  });

  factory ChatProduct.fromJson(Map<String, dynamic> json) => ChatProduct(
        productId: (json['product_id'] ?? '').toString(),
        productName: (json['product_name'] ?? '').toString(),
        variantId: json['variant_id']?.toString(),
        sku: json['sku']?.toString(),
        ram: json['ram']?.toString(),
        storage: json['storage']?.toString(),
        color: json['color']?.toString(),
        imageUrl: json['image_url']?.toString(),
        originalPrice: _money(json['original_price']),
        effectivePrice: _money(json['effective_price']),
        availableQuantity: _int(json['available_quantity']),
        warrantyMonths: _int(json['warranty_months']),
        reasons: _strings(json['reasons']),
      );
}

/// Knowledge document used for the answer (`SourceReference`).
class ChatSource {
  final String documentId, title;
  final String? chunkId, source;

  const ChatSource({required this.documentId, required this.title, this.chunkId, this.source});

  factory ChatSource.fromJson(Map<String, dynamic> json) => ChatSource(
        documentId: (json['document_id'] ?? '').toString(),
        chunkId: json['chunk_id']?.toString(),
        title: (json['title'] ?? '').toString(),
        source: json['source']?.toString(),
      );
}

class ComparedProduct {
  final String productId, productName;
  const ComparedProduct({required this.productId, required this.productName});
}

class ComparisonRow {
  final String key, label;
  final List<String> values;
  /// Indexes of products whose value is measurably better.
  final List<int> best;
  const ComparisonRow({required this.key, required this.label, required this.values, this.best = const []});
}

/// Side-by-side comparison of 2-3 same-category products (`ProductComparison`).
class ChatComparison {
  final String? categoryName;
  final List<ComparedProduct> products;
  final List<ComparisonRow> rows;

  const ChatComparison({this.categoryName, required this.products, required this.rows});

  /// null when there is nothing to draw (fewer than 2 products or no rows), like the web table.
  static ChatComparison? fromJson(Object? value) {
    if (value is! Map) return null;
    final json = Map<String, dynamic>.from(value);
    final products = _maps(json['products'])
        .map((p) => ComparedProduct(productId: (p['product_id'] ?? '').toString(), productName: (p['product_name'] ?? '').toString()))
        .toList();
    final rows = _maps(json['rows'])
        .map((r) => ComparisonRow(
              key: (r['key'] ?? '').toString(),
              label: (r['label'] ?? '').toString(),
              values: _strings(r['values']),
              best: (r['best'] is List ? r['best'] as List : const []).map(_int).whereType<int>().toList(),
            ))
        .toList();
    if (products.length < 2 || rows.isEmpty) return null;
    return ChatComparison(categoryName: json['category_name']?.toString(), products: products, rows: rows);
  }
}

/// Response of `POST /sessions/{id}/messages` (`ChatResponse`).
class ChatReply {
  final String message;
  final List<ChatProduct> products;
  final List<ChatSource> sources;
  final List<String> suggestions;
  final ChatComparison? comparison;

  const ChatReply({
    required this.message,
    this.products = const [],
    this.sources = const [],
    this.suggestions = const [],
    this.comparison,
  });

  factory ChatReply.fromJson(Map<String, dynamic> json) => ChatReply(
        message: (json['message'] ?? '').toString(),
        products: _maps(json['products']).map(ChatProduct.fromJson).toList(),
        sources: _maps(json['sources']).map(ChatSource.fromJson).toList(),
        suggestions: _strings(json['suggested_questions']),
        comparison: ChatComparison.fromJson(json['comparison']),
      );
}

/// Stored conversation (`ChatSession`); sessions have no title.
class ChatSessionInfo {
  final String id;
  final DateTime? createdAt;
  final bool isActive;

  const ChatSessionInfo({required this.id, this.createdAt, this.isActive = true});

  factory ChatSessionInfo.fromJson(Map<String, dynamic> json) => ChatSessionInfo(
        id: (json['id'] ?? '').toString(),
        createdAt: _date(json['created_at']),
        isActive: json['is_active'] != false,
      );
}

/// One stored message from `GET /sessions/{id}/messages` (text only).
class ChatHistoryRow {
  final String id, senderType, message;
  final DateTime? createdAt;

  const ChatHistoryRow({required this.id, required this.senderType, required this.message, this.createdAt});

  bool get fromAssistant => senderType == 'ASSISTANT';

  factory ChatHistoryRow.fromJson(Map<String, dynamic> json) => ChatHistoryRow(
        id: (json['id'] ?? '').toString(),
        senderType: (json['sender_type'] ?? '').toString(),
        message: (json['message'] ?? '').toString(),
        createdAt: _date(json['created_at']),
      );
}

/// A message on screen: live answers keep their cards; history rows are text only.
class ChatItem {
  final String id;
  /// History id on the server; null for messages not yet confirmed by history.
  final String? serverId;
  final bool fromAssistant;
  final String text;
  final DateTime? createdAt;
  final bool pending;
  final List<ChatProduct> products;
  final List<ChatSource> sources;
  final List<String> suggestions;
  final ChatComparison? comparison;

  const ChatItem({
    required this.id,
    this.serverId,
    required this.fromAssistant,
    required this.text,
    this.createdAt,
    this.pending = false,
    this.products = const [],
    this.sources = const [],
    this.suggestions = const [],
    this.comparison,
  });

  bool get hasExtras => products.isNotEmpty || sources.isNotEmpty || suggestions.isNotEmpty || comparison != null;

  ChatItem confirmed() => ChatItem(
        id: id,
        serverId: serverId,
        fromAssistant: fromAssistant,
        text: text,
        createdAt: createdAt,
        products: products,
        sources: sources,
        suggestions: suggestions,
        comparison: comparison,
      );
}
