import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../products/data/models/product_model.dart';
import '../../products/presentation/pages/product_detail_screen.dart';
import '../../products/presentation/widgets/shop_widgets.dart';
import '../data/chat_logic.dart';
import '../data/chat_models.dart';

// Mobile counterparts of the web assistant parts (`CustomerChatParts.jsx`).

void openChatProduct(BuildContext context, String productId, String productName, {ChatProduct? card}) {
  Navigator.push(
    context,
    MaterialPageRoute(
      builder: (_) => ProductDetailScreen(
        product: Product(
          id: productId,
          name: productName,
          price: card?.effectivePrice,
          originalPrice: card?.originalPrice,
          availableQuantity: card?.availableQuantity ?? 0,
          imageUrl: card?.imageUrl,
          brand: '',
        ),
      ),
    ),
  );
}

String _stockText(int? quantity) {
  // null means the backend could not determine stock; never present it as sold out.
  if (quantity == null) return 'Tồn kho: Chưa xác định';
  return quantity > 0 ? 'Tồn kho: còn $quantity' : 'Tồn kho: hết hàng';
}

class ChatProductCard extends StatelessWidget {
  final ChatProduct product;
  const ChatProductCard({super.key, required this.product});

  @override
  Widget build(BuildContext context) {
    final specs = [
      if (product.ram != null) 'RAM ${product.ram}',
      if (product.storage != null) 'Bộ nhớ ${product.storage}',
      if (product.color != null) 'Màu ${product.color}',
    ];
    final meta = [
      _stockText(product.availableQuantity),
      if (product.warrantyMonths != null) 'Bảo hành ${product.warrantyMonths} tháng',
      if (product.sku != null) 'SKU ${product.sku}',
    ];
    final effective = product.effectivePrice, original = product.originalPrice;
    final discounted = effective != null && original != null && original > effective;
    void open() => openChatProduct(context, product.productId, product.productName, card: product);
    return Card(
      margin: const EdgeInsets.only(top: 8),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: open,
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                width: 72,
                height: 72,
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: ProductImage(
                    product: Product(id: product.productId, name: product.productName, price: effective,
                        availableQuantity: 0, imageUrl: product.imageUrl, brand: ''),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(product.productName, style: const TextStyle(fontWeight: FontWeight.w700, color: shopInk)),
                    const SizedBox(height: 2),
                    if (effective == null)
                      const Text('Chưa có giá', style: TextStyle(color: Colors.grey))
                    else
                      Wrap(
                        spacing: 6,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [
                          Text(money(effective), style: const TextStyle(color: shopOrange, fontWeight: FontWeight.w800)),
                          if (discounted)
                            Text(money(original),
                                style: const TextStyle(color: Colors.grey, fontSize: 12, decoration: TextDecoration.lineThrough)),
                        ],
                      ),
                    if (specs.isNotEmpty) Text(specs.join(' · '), style: const TextStyle(fontSize: 12)),
                    Text(meta.join(' · '), style: TextStyle(fontSize: 12, color: Colors.grey.shade700)),
                    for (final reason in product.reasons)
                      Text('• $reason', style: const TextStyle(fontSize: 12, color: shopBlue)),
                    Align(
                      alignment: Alignment.centerRight,
                      child: TextButton(onPressed: open, child: const Text('Xem chi tiết')),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Values arrive formatted by the chatbot from verified catalog data; `best` marks measurably better cells only.
class ChatComparisonTable extends StatelessWidget {
  final ChatComparison comparison;
  const ChatComparisonTable({super.key, required this.comparison});

  @override
  Widget build(BuildContext context) {
    final products = comparison.products;
    final header = TextStyle(fontWeight: FontWeight.w700, color: shopInk, fontSize: 12);
    Widget cell(Widget child, {Color? color}) =>
        Container(color: color, padding: const EdgeInsets.all(6), constraints: const BoxConstraints(minWidth: 110, maxWidth: 160), child: child);
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('So sánh thông số${comparison.categoryName == null ? '' : ' · ${comparison.categoryName}'}',
              style: const TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Table(
              defaultColumnWidth: const IntrinsicColumnWidth(),
              border: TableBorder.all(color: const Color(0xffe0e6eb)),
              children: [
                TableRow(
                  decoration: const BoxDecoration(color: shopTint),
                  children: [
                    cell(Text('Thông số', style: header)),
                    for (final p in products)
                      cell(InkWell(
                        onTap: () => openChatProduct(context, p.productId, p.productName),
                        child: Text(p.productName, style: header.copyWith(color: shopBlue, decoration: TextDecoration.underline)),
                      )),
                  ],
                ),
                for (final row in comparison.rows)
                  TableRow(children: [
                    cell(Text(row.label, style: header)),
                    for (var i = 0; i < products.length; i++)
                      cell(
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(i < row.values.length ? row.values[i] : '—', style: const TextStyle(fontSize: 12)),
                            if (row.best.contains(i))
                              const Text('Tốt hơn', style: TextStyle(fontSize: 11, color: Colors.green, fontWeight: FontWeight.w700)),
                          ],
                        ),
                        color: row.best.contains(i) ? Colors.green.shade50 : null,
                      ),
                  ]),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class ChatSourceList extends StatelessWidget {
  final List<ChatSource> sources;
  const ChatSourceList({super.key, required this.sources});

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Nguồn tham khảo', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 12)),
            for (final source in sources)
              Builder(builder: (context) {
                final href = safeHttpUrl(source.source);
                final label = Text('• ${source.title}',
                    style: TextStyle(fontSize: 12, color: href == null ? null : shopBlue, decoration: href == null ? null : TextDecoration.underline));
                return href == null
                    ? label
                    : InkWell(onTap: () => launchUrl(href, mode: LaunchMode.externalApplication), child: label);
              }),
          ],
        ),
      );
}

class ChatBubble extends StatelessWidget {
  final ChatItem item;
  final ValueChanged<String> onSuggestion;
  final bool suggestionsDisabled;
  const ChatBubble({super.key, required this.item, required this.onSuggestion, required this.suggestionsDisabled});

  @override
  Widget build(BuildContext context) {
    final assistant = item.fromAssistant;
    final time = messageTime(item.createdAt);
    return Align(
      alignment: assistant ? Alignment.centerLeft : Alignment.centerRight,
      child: Container(
        constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * (assistant ? 0.92 : 0.8)),
        margin: const EdgeInsets.symmetric(vertical: 4),
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: assistant ? Colors.white : Colors.deepOrange.shade50,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: const Color(0xffe0e6eb)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(assistant ? 'Trợ lý Điện Việt' : 'Bạn', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 12)),
                if (time.isNotEmpty) Text('  $time', style: TextStyle(fontSize: 11, color: Colors.grey.shade600)),
              ],
            ),
            const SizedBox(height: 4),
            // Plain text only: model output is never interpreted as markup.
            SelectableText(item.text),
            if (item.pending) Text('Đang gửi…', style: TextStyle(fontSize: 11, color: Colors.grey.shade600)),
            if (item.comparison != null) ChatComparisonTable(comparison: item.comparison!),
            for (final product in item.products) ChatProductCard(product: product),
            if (item.sources.isNotEmpty) ChatSourceList(sources: item.sources),
            if (item.suggestions.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Wrap(
                  spacing: 6,
                  runSpacing: 4,
                  children: [
                    for (final question in item.suggestions)
                      ActionChip(
                        label: Text(question, style: const TextStyle(fontSize: 12)),
                        onPressed: suggestionsDisabled ? null : () => onSuggestion(question),
                      ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}
