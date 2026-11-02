class WarrantyTicketStatusValue {
  static String label(String? raw) {
    final value = (raw ?? '').toUpperCase();
    switch (value) {
      case 'RECEIVED':
        return 'Đã tiếp nhận';
      case 'IN_PROGRESS':
        return 'Đang xử lý';
      case 'COMPLETED':
        return 'Hoàn thành';
      case 'REJECTED':
        return 'Từ chối';
      default:
        return raw ?? 'Không xác định';
    }
  }
}

class WarrantyStatusValue {
  static String label(String? raw) {
    final value = (raw ?? '').toUpperCase();
    switch (value) {
      case 'ACTIVE':
        return 'Còn hiệu lực';
      case 'EXPIRED':
        return 'Hết hạn';
      default:
        return raw ?? 'Không xác định';
    }
  }
}

class WarrantyTicketModel {
  final String id;
  final String ticketCode;
  final String warrantyId;
  final String productName;
  final String sku;
  final String serialNumber;
  final List<String> imeiNumbers;
  final String issueDescription;
  final String status;
  final String? createdAt;
  final String? resolvedAt;
  final List<String> allowedStatuses;

  WarrantyTicketModel({
    required this.id,
    required this.ticketCode,
    required this.warrantyId,
    required this.productName,
    required this.sku,
    required this.serialNumber,
    required this.imeiNumbers,
    required this.issueDescription,
    required this.status,
    this.createdAt,
    this.resolvedAt,
    this.allowedStatuses = const [],
  });

  factory WarrantyTicketModel.fromJson(Map<String, dynamic> json) {
    final rawImeis = json['imeiNumbers'] ?? json['imei_numbers'] ?? json['imeis'] ?? const <dynamic>[];
    final imeiNumbers = (rawImeis is List)
        ? rawImeis.map((e) => e?.toString() ?? '').where((e) => e.isNotEmpty).toList()
        : <String>[];

    final allowedStatuses = (json['allowedStatuses'] ?? const <dynamic>[])
        .map((e) => e.toString())
        .toList();

    return WarrantyTicketModel(
      id: (json['ticketId'] ?? json['id'] ?? '').toString(),
      ticketCode: (json['ticketCode'] ?? '').toString(),
      warrantyId: (json['warrantyId'] ?? '').toString(),
      productName: (json['productName'] ?? 'Thiết bị').toString(),
      sku: (json['sku'] ?? '').toString(),
      serialNumber: (json['serialNumber'] ?? '').toString(),
      imeiNumbers: imeiNumbers,
      issueDescription: (json['issueDescription'] ?? '').toString(),
      status: (json['status'] ?? '').toString(),
      createdAt: json['createdAt']?.toString(),
      resolvedAt: json['resolvedAt']?.toString(),
      allowedStatuses: allowedStatuses,
    );
  }
}

class WarrantyModel {
  final String id;
  final String serialId;
  final String orderItemId;
  final String productName;
  final String sku;
  final String serialNumber;
  final List<String> imeiNumbers;
  final String startDate;
  final String endDate;
  final String status;
  final bool eligible;
  final List<WarrantyTicketModel> tickets;

  WarrantyModel({
    required this.id,
    required this.serialId,
    required this.orderItemId,
    required this.productName,
    required this.sku,
    required this.serialNumber,
    required this.imeiNumbers,
    required this.startDate,
    required this.endDate,
    required this.status,
    required this.eligible,
    required this.tickets,
  });

  factory WarrantyModel.fromJson(Map<String, dynamic> json) {
    final rawImeis = json['imeiNumbers'] ?? json['imei_numbers'] ?? const <dynamic>[];
    final rawTickets = json['tickets'] ?? const <dynamic>[];

    final imeiNumbers = (rawImeis is List)
        ? rawImeis.map((e) => e?.toString() ?? '').where((e) => e.isNotEmpty).toList()
        : <String>[];

    final tickets = (rawTickets is List)
        ? rawTickets
            .map((e) => WarrantyTicketModel.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList()
        : <WarrantyTicketModel>[];

    return WarrantyModel(
      id: (json['warrantyId'] ?? json['id'] ?? '').toString(),
      serialId: (json['serialId'] ?? '').toString(),
      orderItemId: (json['orderItemId'] ?? '').toString(),
      productName: (json['productName'] ?? 'Thiết bị').toString(),
      sku: (json['sku'] ?? '').toString(),
      serialNumber: (json['serialNumber'] ?? '').toString(),
      imeiNumbers: imeiNumbers,
      startDate: json['startDate']?.toString() ?? '',
      endDate: json['endDate']?.toString() ?? '',
      status: (json['status'] ?? '').toString(),
      eligible: json['eligible'] == true,
      tickets: tickets,
    );
  }
}
