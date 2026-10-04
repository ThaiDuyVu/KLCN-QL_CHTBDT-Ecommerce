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
  @override
  Widget build(BuildContext context) {
    final path = product.imageUrl;
    final placeholder = Center(
      child: Icon(
        Icons.devices_outlined,
        size: 56,
        color: shopBlue.withValues(alpha: .3),
      ),
    );
    if (path == null || path.isEmpty) return placeholder;
    final uri = Uri.tryParse(path);
    final localPath = uri?.path ?? path;
    if (localPath.startsWith('/images/products/')) {
      return Image.asset(
        localPath.replaceFirst('/images/', 'assets/'),
        fit: BoxFit.contain,
        errorBuilder: (_, _, _) => placeholder,
      );
    }
    final url = Uri.parse(ApiClient.baseUrl).resolve(path).toString();
    return Image.network(
      url,
      fit: BoxFit.contain,
      errorBuilder: (_, _, _) => placeholder,
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
