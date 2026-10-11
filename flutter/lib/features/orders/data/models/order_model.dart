class OrderItem {
  final String id;
  final String productId;
  final String productName;
  final int quantity;
  final double unitPrice;
  final String? serialNumber;
  final List<String> imeiNumbers;

  OrderItem({
    required this.id,
    required this.productId,
    required this.productName,
    required this.quantity,
    required this.unitPrice,
    this.serialNumber,
    this.imeiNumbers = const [],
  });

  factory OrderItem.fromJson(Map<String, dynamic> json) {
    final product = json['product'];
    final productName = json['productName'] ??
        json['product_name'] ??
        (product is Map ? product['name'] ?? product['productName'] : null) ??
        'Sản phẩm';

    final rawImeis = json['imeiNumbers'] ?? json['imei_numbers'] ?? json['imeis'] ?? const <dynamic>[];
    final imeiNumbers = (rawImeis is List)
        ? rawImeis.map((e) => e?.toString() ?? '').where((e) => e.isNotEmpty).toList()
        : <String>[];

    return OrderItem(
      id: (json['id'] ?? json['orderItemId'] ?? json['order_item_id'] ?? json['cartItemId'] ?? '').toString(),
      productId: (json['productId'] ?? json['product_id'] ?? json['variantId'] ?? '').toString(),
      productName: productName.toString(),
      quantity: ((json['quantity'] ?? 0) as num).toInt(),
      unitPrice: ((json['unitPrice'] ?? json['unit_price'] ?? json['price'] ?? 0) as num).toDouble(),
      serialNumber: (json['serialNumber'] ?? json['serial_number'] ?? json['serial'] ?? json['deviceSerial'])?.toString(),
      imeiNumbers: imeiNumbers,
    );
  }
}

class Order {
  final String id;
  final String orderCode;
  final String status;
  final double totalAmount;
  final String? createdAt;
  final String? warehouseName;
  final List<OrderItem> items;
  final String? shippingAddress;
  final List<String> allowedStatuses;
  final bool canCancel;

  Order({
    required this.id,
    required this.orderCode,
    required this.status,
    required this.totalAmount,
    this.createdAt,
    this.warehouseName,
    required this.items,
    this.shippingAddress,
    this.allowedStatuses = const [],
    this.canCancel = false,
  });

  static String statusLabel(String value) {
    final normalized = value.toUpperCase();
    switch (normalized) {
      case 'PENDING':
        return 'Chờ xác nhận';
      case 'CONFIRMED':
        return 'Đã xác nhận';
      case 'PROCESSING':
        return 'Đang chuẩn bị hàng';
      case 'SHIPPED':
        return 'Đang giao';
      case 'DELIVERED':
        return 'Đã giao';
      case 'CANCELLED':
        return 'Đã hủy';
      default:
        return value;
    }
  }

  factory Order.fromJson(Map<String, dynamic> json) {
    final itemsJson = json['items'] ?? json['orderItems'] ?? json['lineItems'] ?? const <dynamic>[];
    final total = ((json['totalAmount'] ?? json['total'] ?? json['amount'] ?? 0) as num).toDouble();
    final rawAllowedStatuses = json['allowedStatuses'] ?? json['allowed_statuses'] ?? json['cancelableStatuses'] ?? const <dynamic>[];
    final allowedStatuses = (rawAllowedStatuses is List)
        ? rawAllowedStatuses.map((e) => e.toString().toUpperCase()).toSet().toList()
        : <String>[];

    final status = (json['status'] ?? json['orderStatus'] ?? json['order_status'] ?? 'PENDING').toString().toUpperCase();

    return Order(
      id: (json['id'] ?? json['orderId'] ?? json['order_id'] ?? '').toString(),
      orderCode: (json['orderCode'] ?? json['order_code'] ?? json['code'] ?? json['id'] ?? json['orderId'] ?? '').toString(),
      status: status,
      totalAmount: total,
      createdAt: json['orderDate']?.toString() ?? json['order_date']?.toString() ?? json['createdAt']?.toString() ?? json['created_at']?.toString(),
      warehouseName: json['warehouseName']?.toString() ?? json['warehouse_name']?.toString() ?? json['branchName']?.toString(),
      items: (itemsJson as List)
          .map((e) => OrderItem.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList(),
      shippingAddress: json['shippingAddress']?.toString() ?? json['shipping_address']?.toString() ?? json['address']?.toString(),
      allowedStatuses: allowedStatuses,
      canCancel: allowedStatuses.contains('CANCELLED'),
    );
  }
}
