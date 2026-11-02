import 'package:flutter/material.dart';
import '../../../../core/network/api_client.dart';
import '../../data/models/product_model.dart';

const shopBlue = Color(0xff256080);
const shopOrange = Color(0xffc75b10);
const shopInk = Color(0xff23364a);
const shopTint = Color(0xfff0f8fb);

String money(double? value) {
  if (value == null) return 'Xem giá phiên bản';
  final digits = value.round().toString();
  return '${digits.replaceAllMapped(RegExp(r'(\d)(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} ₫';
}

class ProductImage extends StatelessWidget {
  final Product product;
  const ProductImage({super.key, required this.product});

  static String fixUrl(String rawUrl) => ApiClient.fixUrl(rawUrl);

  static String? findLocalAsset(String name) {
    final lower = name.toLowerCase();
    if (lower.contains('16') || lower.contains('promax') || lower.contains('pro max')) {
      return 'assets/products/iphone-16/main.jpg';
    }
    if (lower.contains('15')) {
      if (lower.contains('xiaomi')) return 'assets/products/xiaomi-15/main.jpg';
      if (lower.contains('vivobook')) return 'assets/products/asus-vivobook-15/main.jpg';
      if (lower.contains('inspiron')) return 'assets/products/dell-inspiron-15/main.jpg';
      return 'assets/products/iphone-15/main.jpg';
    }
    if (lower.contains('14') && !lower.contains('14t') && !lower.contains('pro 14') && !lower.contains('g14')) {
      return 'assets/products/iphone-14/main.jpg';
    }
    if (lower.contains('s24')) return 'assets/products/samsung-galaxy-s24/main.jpg';
    if (lower.contains('s23')) return 'assets/products/samsung-galaxy-s23/main.jpg';
    if (lower.contains('a55')) return 'assets/products/samsung-galaxy-a55/main.jpg';
    if (lower.contains('flip')) return 'assets/products/samsung-galaxy-z-flip6/main.jpg';
    if (lower.contains('14t')) return 'assets/products/xiaomi-14t-pro/main.jpg';
    if (lower.contains('redmi') || lower.contains('note 13')) {
      return 'assets/products/xiaomi-redmi-note-13/main.jpg';
    }
    if (lower.contains('air m2')) return 'assets/products/macbook-air-m2/main.jpg';
    if (lower.contains('pro 14') || lower.contains('m3')) {
      return 'assets/products/macbook-pro-14-m3/main.jpg';
    }
    if (lower.contains('xps')) return 'assets/products/dell-xps-13/main.jpg';
    if (lower.contains('inspiron')) return 'assets/products/dell-inspiron-15/main.jpg';
    if (lower.contains('vivobook')) return 'assets/products/asus-vivobook-15/main.jpg';
    if (lower.contains('zephyrus') || lower.contains('g14') || lower.contains('rog')) {
      return 'assets/products/asus-rog-zephyrus-g14/main.jpg';
    }
    return null;
  }

  Widget _buildLocalFallback() {
    final assetPath = findLocalAsset(product.name);
    final placeholder = Center(
      child: Icon(
        Icons.devices_outlined,
        size: 56,
        color: shopBlue.withValues(alpha: .3),
      ),
    );
    if (assetPath != null) {
      return Image.asset(
        assetPath,
        fit: BoxFit.cover,
        errorBuilder: (_, _, _) => placeholder,
      );
    }
    return placeholder;
  }

  @override
  Widget build(BuildContext context) {
    final rawPath = product.imageUrl;
    if (rawPath == null || rawPath.trim().isEmpty) {
      return _buildLocalFallback();
    }

    final path = rawPath.trim();
    final fixUrl = ApiClient.fixUrl(path);

    final uri = Uri.tryParse(path);
    final localPath = uri?.path ?? path;
    if (localPath.startsWith('/images/products/')) {
      return Image.asset(
        localPath.replaceFirst('/images/', 'assets/'),
        fit: BoxFit.cover,
        errorBuilder: (_, _, _) => Image.network(
          fixUrl,
          fit: BoxFit.cover,
          errorBuilder: (_, _, _) => _buildLocalFallback(),
        ),
      );
    }
    return Image.network(
      fixUrl,
      fit: BoxFit.cover,
      errorBuilder: (_, _, _) => _buildLocalFallback(),
    );
  }
}

class ProductPrice extends StatelessWidget {
  final Product product;
  const ProductPrice({super.key, required this.product});
  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(
        money(product.price),
        style: const TextStyle(
          color: shopOrange,
          fontSize: 17,
          fontWeight: FontWeight.w800,
        ),
      ),
      if (product.price != null &&
          product.originalPrice != null &&
          product.originalPrice! > product.price!)
        Text(
          money(product.originalPrice),
          style: const TextStyle(
            color: Colors.grey,
            fontSize: 12,
            decoration: TextDecoration.lineThrough,
          ),
        ),
    ],
  );
}

class ShopMessage extends StatelessWidget {
  final IconData icon;
  final String title, message;
  final VoidCallback? retry;
  const ShopMessage({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    this.retry,
  });
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 48, horizontal: 24),
    child: Column(
      children: [
        Icon(icon, size: 48, color: shopBlue),
        const SizedBox(height: 16),
        Text(
          title,
          textAlign: TextAlign.center,
          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
        ),
        const SizedBox(height: 8),
        Text(message, textAlign: TextAlign.center),
        if (retry != null)
          TextButton(onPressed: retry, child: const Text('Thử lại')),
      ],
    ),
  );
}
