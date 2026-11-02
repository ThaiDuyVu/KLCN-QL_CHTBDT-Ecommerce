class Product {
  final String id, name, brand, category, status;
  final double? price, originalPrice;
  final int availableQuantity;
  final String? imageUrl, description;
  Product({
    required this.id,
    required this.name,
    required this.price,
    required this.availableQuantity,
    this.imageUrl,
    this.description,
    required this.brand,
    this.category = '',
    this.status = 'ACTIVE',
    this.originalPrice,
  });

  String? get primaryImageUrl => imageUrl;
  String get productName => name;

  factory Product.fromJson(Map<String, dynamic> json) => Product(
    id: (json['productId'] ?? '').toString(),
    name: (json['productName'] ?? 'Sản phẩm').toString(),
    price: (json['effectivePrice'] as num?)?.toDouble(),
    originalPrice: (json['originalPrice'] as num?)?.toDouble(),
    availableQuantity: (json['availableQuantity'] as num?)?.toInt() ?? 0,
    imageUrl: (json['primaryImageUrl'] as String?)
        ?.replaceAll('localhost', '127.0.0.1')
        .replaceAll('10.0.2.2', '127.0.0.1')
        .replaceAll(RegExp(r'192\.168\.\d+\.\d+'), '127.0.0.1'),
    description: json['description'] as String?,
    brand: (json['brandName'] ?? '').toString(),
    category: (json['categoryName'] ?? '').toString(),
    status: (json['status'] ?? '').toString(),
  );
}
