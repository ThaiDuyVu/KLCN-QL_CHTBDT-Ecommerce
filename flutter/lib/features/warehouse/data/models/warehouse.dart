class Warehouse {
  final String id; 
  final String name;
  final String? address;

  Warehouse({required this.id, required this.name, this.address});

  factory Warehouse.fromJson(Map<String, dynamic> json) {
    return Warehouse(
      id: (json['warehouseId'] ?? json['id'] ?? '').toString(),
      name: (json['warehouseName'] ?? json['name'] ?? 'Chi nhánh chưa đặt tên').toString(),
      address: json['address']?.toString(),
    );
  }
}