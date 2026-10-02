class WarrantyTicket {
  final String id;
  final String productName;
  final String? serialNumber;
  final String? imei;
  final String status;
  final String? createdAt;

  WarrantyTicket({
    required this.id,
    required this.productName,
    this.serialNumber,
    this.imei,
    required this.status,
    this.createdAt,
  });

  factory WarrantyTicket.fromJson(Map<String, dynamic> json) {
    final product = json['product'];

    return WarrantyTicket(
      id: (json['id'] ?? json['warrantyId'] ?? json['ticketId'] ?? '').toString(),
      productName: (json['productName'] ?? (product is Map ? product['name'] : null) ?? 'Thiết bị').toString(),
      serialNumber: json['serialNumber']?.toString() ?? json['serial']?.toString(),
      imei: json['imei']?.toString() ?? json['deviceImei']?.toString(),
      status: (json['status'] ?? 'OPEN').toString(),
      createdAt: json['createdAt']?.toString() ?? json['created_at']?.toString(),
    );
  }
}
