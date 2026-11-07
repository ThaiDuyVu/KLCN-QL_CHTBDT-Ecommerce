class OrderItem {
  final String id;
  final String productId;
  final String productName;
  final String sku;
  final int quantity;
  final double unitPrice;
  final double discountAmount;
  final double finalUnitPrice;
  final String? serialNumber;
  final List<String> imeiNumbers;

  OrderItem({
    required this.id,
    required this.productId,
    required this.productName,
    this.sku = '',
    required this.quantity,
    required this.unitPrice,
    this.discountAmount = 0,
    double? finalUnitPrice,
    this.serialNumber,
    this.imeiNumbers = const [],
  }) : finalUnitPrice = finalUnitPrice ?? unitPrice;

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

    final unitPrice = ((json['unitPrice'] ?? json['unit_price'] ?? json['price'] ?? 0) as num).toDouble();
    final discount = ((json['discountAmount'] ?? json['discount_amount'] ?? 0) as num).toDouble();
    final finalPrice = json['finalUnitPrice'] != null || json['final_unit_price'] != null
        ? ((json['finalUnitPrice'] ?? json['final_unit_price']) as num).toDouble()
        : (unitPrice - discount).clamp(0, double.infinity).toDouble();

    return OrderItem(
      id: (json['id'] ?? json['orderItemId'] ?? json['order_item_id'] ?? json['cartItemId'] ?? '').toString(),
      productId: (json['productId'] ?? json['product_id'] ?? json['variantId'] ?? '').toString(),
      productName: productName.toString(),
      sku: (json['sku'] ?? '').toString(),
      quantity: ((json['quantity'] ?? 0) as num).toInt(),
      unitPrice: unitPrice,
      discountAmount: discount,
      finalUnitPrice: finalPrice,
      serialNumber: (json['serialNumber'] ?? json['serial_number'] ?? json['serial'] ?? json['deviceSerial'])?.toString(),
      imeiNumbers: imeiNumbers,
    );
  }
}

/// Delivery leg of an order (backend `shipment`): carrier, delivery employee and timestamps.
class OrderShipment {
  final String status;
  final String? employeeName;
  final String? employeePhone;
  final String shippingProvider;
  final String trackingNumber;
  final String? shippedDate;
  final String? deliveredDate;

  const OrderShipment({
    required this.status,
    this.employeeName,
    this.employeePhone,
    required this.shippingProvider,
    required this.trackingNumber,
    this.shippedDate,
    this.deliveredDate,
  });

  static String statusLabel(String value) {
    switch (value.toUpperCase()) {
      case 'SHIPPING':
        return 'Đang giao';
      case 'DELIVERED':
        return 'Đã giao';
      case 'FAILED':
        return 'Giao không thành công';
      default:
        return value;
    }
  }

  factory OrderShipment.fromJson(Map<String, dynamic> json) => OrderShipment(
        status: (json['status'] ?? 'SHIPPING').toString().toUpperCase(),
        employeeName: json['employeeName']?.toString(),
        employeePhone: json['employeePhone']?.toString(),
        shippingProvider: (json['shippingProvider'] ?? '').toString(),
        trackingNumber: (json['trackingNumber'] ?? '').toString(),
        shippedDate: json['shippedDate']?.toString(),
        deliveredDate: json['deliveredDate']?.toString(),
      );
}

class Order {
  final String id;
  final String orderCode;
  final String status;
  final double totalAmount;
  final double subtotal;
  final double discountAmount;
  final double shippingFee;
  final String? createdAt;
  final String? warehouseName;
  final String recipientName;
  final String recipientPhone;
  final String? shippingAddress;
  final String? note;
  /// Why the order was cancelled (customer, staff, failed delivery or VNPAY expiry).
  final String? cancelReason;
  final String paymentMethod;
  final String paymentStatus;
  final List<OrderItem> items;
  final List<String> allowedStatuses;
  final bool canCancel;
  final OrderShipment? shipment;
  /// Sales invoice number, issued when the order is delivered.
  final String? invoiceNumber;
  final String? invoiceStatus;

  Order({
    required this.id,
    required this.orderCode,
    required this.status,
    required this.totalAmount,
    double? subtotal,
    this.discountAmount = 0,
    this.shippingFee = 0,
    this.createdAt,
    this.warehouseName,
    this.recipientName = '',
    this.recipientPhone = '',
    this.shippingAddress,
    this.note,
    this.cancelReason,
    this.paymentMethod = 'COD',
    this.paymentStatus = 'PENDING',
    required this.items,
    this.allowedStatuses = const [],
    this.canCancel = false,
    this.shipment,
    this.invoiceNumber,
    this.invoiceStatus,
  }) : subtotal = subtotal ?? totalAmount;

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
    final subtotal = json['subtotal'] != null || json['sub_total'] != null
        ? ((json['subtotal'] ?? json['sub_total']) as num).toDouble()
        : total;
    final discount = ((json['discountAmount'] ?? json['discount_amount'] ?? 0) as num).toDouble();
    final shipping = ((json['shippingFee'] ?? json['shipping_fee'] ?? 0) as num).toDouble();

    final rawAllowedStatuses = json['allowedStatuses'] ?? json['allowed_statuses'] ?? json['cancelableStatuses'] ?? const <dynamic>[];
    final allowedStatuses = (rawAllowedStatuses is List)
        ? rawAllowedStatuses.map((e) => e.toString().toUpperCase()).toSet().toList()
        : <String>[];

    final status = (json['status'] ?? json['orderStatus'] ?? json['order_status'] ?? 'PENDING').toString().toUpperCase();

    final payment = json['payment'] is Map ? json['payment'] as Map : null;
    final paymentMethod = payment?['paymentMethod']?.toString() ?? json['paymentMethod']?.toString() ?? 'COD';
    final paymentStatus = payment?['status']?.toString() ?? json['paymentStatus']?.toString() ?? 'PENDING';

    return Order(
      id: (json['id'] ?? json['orderId'] ?? json['order_id'] ?? '').toString(),
      orderCode: (json['orderCode'] ?? json['order_code'] ?? json['code'] ?? json['id'] ?? json['orderId'] ?? '').toString(),
      status: status,
      totalAmount: total,
      subtotal: subtotal,
      discountAmount: discount,
      shippingFee: shipping,
      createdAt: json['orderDate']?.toString() ?? json['order_date']?.toString() ?? json['createdAt']?.toString() ?? json['created_at']?.toString(),
      warehouseName: json['warehouseName']?.toString() ?? json['warehouse_name']?.toString() ?? json['branchName']?.toString(),
      recipientName: (json['recipientName'] ?? json['recipient_name'] ?? json['customerName'] ?? '').toString(),
      recipientPhone: (json['recipientPhone'] ?? json['recipient_phone'] ?? json['customerPhone'] ?? '').toString(),
      shippingAddress: json['shippingAddress']?.toString() ?? json['shipping_address']?.toString() ?? json['address']?.toString(),
      note: json['note']?.toString(),
      cancelReason: json['cancelReason']?.toString(),
      paymentMethod: paymentMethod,
      paymentStatus: paymentStatus,
      items: (itemsJson as List)
          .map((e) => OrderItem.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList(),
      allowedStatuses: allowedStatuses,
      canCancel: allowedStatuses.contains('CANCELLED'),
      shipment: json['shipment'] is Map
          ? OrderShipment.fromJson(Map<String, dynamic>.from(json['shipment'] as Map))
          : null,
      invoiceNumber: json['invoice'] is Map ? (json['invoice'] as Map)['invoiceNumber']?.toString() : null,
      invoiceStatus: json['invoice'] is Map ? (json['invoice'] as Map)['status']?.toString() : null,
    );
  }
}
