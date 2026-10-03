class OrderItem {
  final String id;
  final String productId;
  final String productName;
  final int quantity;
  final double unitPrice;
  final String? serialNumber;
  final String? imei;

  OrderItem({
    required this.id,
    required this.productId,
    required this.productName,
    required this.quantity,
    required this.unitPrice,
    this.serialNumber,
    this.imei,
  });

  factory OrderItem.fromJson(Map<String, dynamic> json) {
    final product = json['product'];
    final productName = json['productName'] ??
        (product is Map ? product['name'] : null) ??
        'Sản phẩm';

    return OrderItem(
      id: (json['id'] ?? json['orderItemId'] ?? json['cartItemId'] ?? '').toString(),
      productId: (json['productId'] ?? json['product_id'] ?? '').toString(),
      productName: productName.toString(),
      quantity: ((json['quantity'] ?? 0) as num).toInt(),
      unitPrice: ((json['unitPrice'] ?? json['price'] ?? 0) as num).toDouble(),
      serialNumber: (json['serialNumber'] ?? json['serial'] ?? json['deviceSerial'])?.toString(),
      imei: (json['imei'] ?? json['deviceImei'] ?? json['imeiNumber'])?.toString(),
    );
  }
}

class Order {
  final String id;
  final String status;
  final double totalAmount;
  final String? createdAt;
  final List<OrderItem> items;
  final String? shippingAddress;
  final List<String> allowedStatuses;
  final bool canCancel;

  Order({
    required this.id,
    required this.status,
    required this.totalAmount,
    this.createdAt,
    required this.items,
    this.shippingAddress,
    this.allowedStatuses = const [],
    this.canCancel = false,
  });

  factory Order.fromJson(Map<String, dynamic> json) {
    final itemsJson = json['items'] ?? json['orderItems'] ?? json['lineItems'] ?? const <dynamic>[];
    final total = ((json['totalAmount'] ?? json['total'] ?? json['amount'] ?? 0) as num).toDouble();
    final rawAllowedStatuses = json['allowedStatuses'] ?? json['cancelableStatuses'] ?? const <dynamic>[];
    final allowedStatuses = (rawAllowedStatuses as List)
        .map((e) => e.toString())
        .toList();

    final status = (json['status'] ?? json['orderStatus'] ?? 'PENDING').toString();
    final bool backendFlag = json['canCancel'] == true || json['cancelAllowed'] == true;

    return Order(
      id: (json['id'] ?? json['orderId'] ?? '').toString(),
      status: status,
      totalAmount: total,
      createdAt: json['createdAt']?.toString() ?? json['created_at']?.toString(),
      items: (itemsJson as List)
          .map((e) => OrderItem.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList(),
      shippingAddress: json['shippingAddress']?.toString() ?? json['address']?.toString(),
      allowedStatuses: allowedStatuses,
      canCancel: backendFlag || allowedStatuses.contains(status),
    );
  }
}
