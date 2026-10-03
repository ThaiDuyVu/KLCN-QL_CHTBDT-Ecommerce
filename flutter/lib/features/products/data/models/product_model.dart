class Product {
  final String id;
  final String name;
  final double price;
  final int availableQuantity; 
  final String? imageUrl;
  final String? description;
  final String brand;

  Product({
    required this.id,
    required this.name,
    required this.price,
    required this.availableQuantity,
    this.imageUrl,
    this.description,
    required this.brand,
  });

  factory Product.fromJson(Map<String, dynamic> json) {
    return Product(
      id: (json['productId'] ?? '').toString(),
      name: (json['productName'] ?? 'Sản phẩm').toString(),
      price: (json['price'] ?? 0).toDouble(), 
      // Backend không có quantity, tạm giả lập là 10 để bấm được nút "Thêm vào giỏ"
      availableQuantity: (json['availableQuantity'] ?? json['quantity'] ?? 10).toInt(), 
      imageUrl: json['imageUrl']?.toString(),
      description: json['description']?.toString(),
      brand: (json['brandName'] ?? 'Không rõ').toString(),
    );
  }
}