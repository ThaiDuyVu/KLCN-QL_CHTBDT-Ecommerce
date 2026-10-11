class AddressModel {
  final String id;
  final String? label;
  final String recipientName;
  final String phoneNumber;
  final String detailAddress;
  final String ward;
  final String district;
  final String province;
  final bool isDefault;
  final String? fullAddress;

  const AddressModel({
    required this.id,
    this.label,
    required this.recipientName,
    required this.phoneNumber,
    required this.detailAddress,
    this.ward = '',
    this.district = '',
    this.province = '',
    this.isDefault = false,
    this.fullAddress,
  });

  String get recipientPhone => phoneNumber;
  String get addressLine => detailAddress;

  String get formattedAddress {
    if (fullAddress != null && fullAddress!.trim().isNotEmpty) {
      return fullAddress!.trim();
    }
    final parts = [detailAddress, ward, district, province]
        .where((s) => s.trim().isNotEmpty)
        .toList();
    return parts.join(', ');
  }

  factory AddressModel.fromJson(Map<String, dynamic> json) {
    return AddressModel(
      id: (json['addressId'] ?? json['id'] ?? '').toString(),
      label: json['label']?.toString(),
      recipientName: (json['recipientName'] ?? '').toString(),
      phoneNumber: (json['phoneNumber'] ?? json['recipientPhone'] ?? '').toString(),
      detailAddress: (json['detailAddress'] ?? json['addressLine'] ?? '').toString(),
      ward: (json['ward'] ?? '').toString(),
      district: (json['district'] ?? '').toString(),
      province: (json['province'] ?? '').toString(),
      isDefault: json['isDefault'] == true,
      fullAddress: json['fullAddress']?.toString(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'recipientName': recipientName,
      'recipientPhone': phoneNumber,
      'addressLine': detailAddress,
      if (ward.isNotEmpty) 'ward': ward,
      if (district.isNotEmpty) 'district': district,
      if (province.isNotEmpty) 'province': province,
      if (label != null && label!.isNotEmpty) 'label': label,
      'isDefault': isDefault,
    };
  }

  AddressModel copyWith({
    String? id,
    String? label,
    String? recipientName,
    String? phoneNumber,
    String? detailAddress,
    String? ward,
    String? district,
    String? province,
    bool? isDefault,
    String? fullAddress,
  }) {
    return AddressModel(
      id: id ?? this.id,
      label: label ?? this.label,
      recipientName: recipientName ?? this.recipientName,
      phoneNumber: phoneNumber ?? this.phoneNumber,
      detailAddress: detailAddress ?? this.detailAddress,
      ward: ward ?? this.ward,
      district: district ?? this.district,
      province: province ?? this.province,
      isDefault: isDefault ?? this.isDefault,
      fullAddress: fullAddress ?? this.fullAddress,
    );
  }
}

typedef Address = AddressModel;