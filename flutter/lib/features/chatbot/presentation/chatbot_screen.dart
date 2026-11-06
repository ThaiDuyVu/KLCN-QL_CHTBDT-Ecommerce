import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_error.dart';
import '../../products/data/models/product_model.dart';
import '../../products/presentation/pages/product_detail_screen.dart';
import '../data/chat_repository.dart';

class _ChatMessage {
  final bool fromUser;
  final String text;
  final List<ChatProduct> products;
  final List<String> suggestions;
  final bool error;

  const _ChatMessage({required this.fromUser, required this.text, this.products = const [], this.suggestions = const [], this.error = false});
}

/// Product consulting assistant on mobile (outline item 5: chatbot on both platforms).
class ChatbotScreen extends ConsumerStatefulWidget {
  const ChatbotScreen({super.key});

  @override
  ConsumerState<ChatbotScreen> createState() => _ChatbotScreenState();
}

class _ChatbotScreenState extends ConsumerState<ChatbotScreen> {
  final TextEditingController _input = TextEditingController();
  final ScrollController _scroll = ScrollController();
  final List<_ChatMessage> _messages = [
    const _ChatMessage(
      fromUser: false,
      text: 'Xin chào! Mình là trợ lý tư vấn của cửa hàng. Bạn cần tìm sản phẩm nào, tầm giá bao nhiêu?',
      suggestions: ['Laptop dưới 20 triệu', 'So sánh iPhone 15 và Galaxy S24', 'Chính sách bảo hành'],
    ),
  ];
  String _sessionId = ChatRepository.newSessionId();
  bool _sending = false;

  @override
  void dispose() {
    _input.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _send([String? preset]) async {
    final text = (preset ?? _input.text).trim();
    if (text.isEmpty || _sending) return;
    if (text.length > ChatRepository.maxMessageLength) return;
    setState(() {
      _messages.add(_ChatMessage(fromUser: true, text: text));
      _sending = true;
      _input.clear();
    });
    _scrollToEnd();
    try {
      final reply = await ref.read(chatRepositoryProvider).send(_sessionId, text);
      setState(() => _messages.add(_ChatMessage(fromUser: false, text: reply.message, products: reply.products, suggestions: reply.suggestions)));
    } on DioException catch (e) {
      setState(() => _messages.add(_ChatMessage(fromUser: false, text: apiErrorMessage(e), error: true)));
    } finally {
      if (mounted) setState(() => _sending = false);
      _scrollToEnd();
    }
  }

  void _scrollToEnd() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) _scroll.animateTo(_scroll.position.maxScrollExtent, duration: const Duration(milliseconds: 250), curve: Curves.easeOut);
    });
  }

  void _openProduct(ChatProduct product) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ProductDetailScreen(
          product: Product(
            id: product.productId,
            name: product.productName,
            price: product.effectivePrice,
            originalPrice: product.originalPrice,
            availableQuantity: 0,
            brand: '',
          ),
        ),
      ),
    );
  }

  String _money(double? value) {
    if (value == null) return '';
    final digits = value.round().toString().replaceAllMapped(RegExp(r'\B(?=(\d{3})+(?!\d))'), (_) => '.');
    return '$digits đ';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Trợ lý tư vấn'),
        actions: [
          IconButton(
            tooltip: 'Cuộc trò chuyện mới',
            icon: const Icon(Icons.add_comment_outlined),
            onPressed: _sending
                ? null
                : () => setState(() {
                      _sessionId = ChatRepository.newSessionId();
                      _messages.removeRange(1, _messages.length);
                    }),
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView.builder(
              controller: _scroll,
              padding: const EdgeInsets.all(12),
              itemCount: _messages.length + (_sending ? 1 : 0),
              itemBuilder: (context, index) {
                if (index == _messages.length) {
                  return const Padding(padding: EdgeInsets.all(8), child: Text('Trợ lý đang trả lời…', style: TextStyle(color: Colors.blueGrey)));
                }
                final m = _messages[index];
                return Align(
                  alignment: m.fromUser ? Alignment.centerRight : Alignment.centerLeft,
                  child: Container(
                    constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.85),
                    margin: const EdgeInsets.symmetric(vertical: 4),
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: m.fromUser ? Colors.deepOrange.shade50 : (m.error ? Colors.red.shade50 : Colors.white),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: const Color(0xffe0e6eb)),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SelectableText(m.text),
                        ...m.products.map((p) => ListTile(
                              dense: true,
                              contentPadding: EdgeInsets.zero,
                              leading: const Icon(Icons.devices_other_outlined),
                              title: Text(p.productName),
                              subtitle: Text(_money(p.effectivePrice), style: const TextStyle(color: Colors.deepOrange)),
                              trailing: const Icon(Icons.chevron_right),
                              onTap: () => _openProduct(p),
                            )),
                        if (m.suggestions.isNotEmpty)
                          Wrap(
                            spacing: 6,
                            children: m.suggestions
                                .map((q) => ActionChip(label: Text(q, style: const TextStyle(fontSize: 12)), onPressed: _sending ? null : () => _send(q)))
                                .toList(),
                          ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 4, 12, 8),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _input,
                      enabled: !_sending,
                      maxLength: ChatRepository.maxMessageLength,
                      minLines: 1,
                      maxLines: 4,
                      textInputAction: TextInputAction.send,
                      onSubmitted: (_) => _send(),
                      decoration: const InputDecoration(hintText: 'Hỏi về sản phẩm…', counterText: '', border: OutlineInputBorder()),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filled(onPressed: _sending ? null : () => _send(), icon: const Icon(Icons.send)),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
