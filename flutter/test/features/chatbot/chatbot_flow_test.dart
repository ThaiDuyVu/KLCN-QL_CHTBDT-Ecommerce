import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:ecommerce_app/features/auth/data/auth_repository.dart';
import 'package:ecommerce_app/features/auth/presentation/providers/auth_provider.dart';
import 'package:ecommerce_app/features/chatbot/data/chat_logic.dart';
import 'package:ecommerce_app/features/chatbot/data/chat_repository.dart';
import 'package:ecommerce_app/features/chatbot/presentation/chatbot_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

class _Adapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  final Object? Function(RequestOptions options) handler;
  _Adapter(this.handler);

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? stream, Future<void>? cancel) async {
    requests.add(options);
    final result = handler(options);
    if (result is DioExceptionType) throw DioException(requestOptions: options, type: result);
    if (result is int) return ResponseBody.fromString('{"detail":"x"}', result, headers: _json);
    return ResponseBody.fromString(jsonEncode(result), 200, headers: _json);
  }

  static final _json = {Headers.contentTypeHeader: ['application/json']};

  @override
  void close({bool force = false}) {}
}

const _sid = '11111111-1111-4111-8111-111111111111';
const _me = {'userId': 'user-1', 'username': 'seed.customer1', 'roleName': 'CUSTOMER'};

Map<String, dynamic> _reply = {
  'session_id': _sid,
  'message': 'Mình gợi ý Dell Inspiron 15.',
  'products': [
    {'product_id': 'p-1', 'product_name': 'Dell Inspiron 15', 'effective_price': 18990000, 'original_price': 19990000,
     'ram': '16GB', 'available_quantity': null, 'warranty_months': 12, 'reasons': ['RAM 16GB như bạn cần']},
  ],
  'sources': [
    {'document_id': 'd-1', 'title': 'Chính sách bảo hành', 'source': 'official://kt-05'},
  ],
  'suggested_questions': ['So sánh với Asus Vivobook 15'],
  'comparison': {
    'products': [
      {'product_id': 'p-1', 'product_name': 'Dell Inspiron 15'},
      {'product_id': 'p-2', 'product_name': 'Asus Vivobook 15'},
    ],
    'rows': [
      {'key': 'ram', 'label': 'RAM', 'values': ['16GB', '8GB'], 'best': [0]},
    ],
  },
};

Map<String, dynamic> _row(String id, String sender, String message) =>
    {'id': id, 'session_id': _sid, 'sender_type': sender, 'message': message, 'created_at': '2026-11-07T08:00:00Z'};

ProviderScope _app(_Adapter adapter, {({String userId, String sessionId})? stored}) {
  final dio = Dio(BaseOptions(baseUrl: 'http://test.local'))..httpClientAdapter = adapter;
  return ProviderScope(
    overrides: [
      authRepositoryProvider.overrideWithValue(AuthRepository(client: dio)),
      chatRepositoryProvider.overrideWithValue(ChatRepository(client: dio, baseUrl: '')),
      if (stored != null) chatSelectedSessionProvider.overrideWith((ref) => stored),
    ],
    child: const MaterialApp(home: ChatbotScreen()),
  );
}

void main() {
  group('Chat repository uses the web endpoints', () {
    test('sessions, history paging, close and send', () async {
      final adapter = _Adapter((o) => o.path.endsWith('/messages') && o.method == 'POST'
          ? _reply
          : o.path.endsWith('/close')
              ? {'id': _sid, 'customer_id': 'c', 'created_at': '2026-11-07T08:00:00Z', 'is_active': false}
              : <Object>[]);
      final repo = ChatRepository(client: Dio(BaseOptions(baseUrl: 'http://test.local'))..httpClientAdapter = adapter, baseUrl: '');

      await repo.sessions(page: 1);
      await repo.history(_sid, before: 'h-9');
      final closed = await repo.close(_sid);
      final reply = await repo.send(_sid, 'Laptop 16GB RAM');

      expect(adapter.requests.map((r) => '${r.method} ${r.path} ${r.queryParameters}').toList(), [
        'GET /api/v1/chat/sessions {page: 1, size: 20}',
        'GET /api/v1/chat/sessions/$_sid/messages {before: h-9}',
        'POST /api/v1/chat/sessions/$_sid/close {}',
        'POST /api/v1/chat/sessions/$_sid/messages {}',
      ]);
      expect(adapter.requests.last.data, {'message': 'Laptop 16GB RAM'});
      expect(closed.isActive, isFalse);
      expect(reply.products.single.availableQuantity, isNull);
      expect(reply.products.single.reasons, ['RAM 16GB như bạn cần']);
      expect(reply.sources.single.title, 'Chính sách bảo hành');
      expect(reply.comparison!.rows.single.best, [0]);
    });

    test('a comparison with fewer than two products is not drawn', () {
      expect(ChatComparison.fromJson({'products': [{'product_id': 'p', 'product_name': 'A'}], 'rows': []}), isNull);
    });
  });

  group('Pure helpers match the web', () {
    test('history keeps cards only for the same assistant text seen live', () {
      final live = [
        ChatItem(id: 'live', fromAssistant: true, text: 'Mình gợi ý Dell Inspiron 15.', products: ChatReply.fromJson(_reply).products),
      ];
      final merged = mergeHistory([
        ChatHistoryRow.fromJson(_row('h-1', 'USER', 'Laptop 16GB')),
        ChatHistoryRow.fromJson(_row('h-2', 'ASSISTANT', 'Mình gợi ý Dell Inspiron 15.')),
        ChatHistoryRow.fromJson(_row('h-3', 'ASSISTANT', 'Câu khác')),
      ], live);
      expect(merged.map((m) => m.serverId), ['h-1', 'h-2', 'h-3']);
      expect(merged[1].products.single.productName, 'Dell Inspiron 15');
      expect(merged[2].products, isEmpty);
    });

    test('a turn counts as answered only when history ends with the question and an answer', () {
      final question = ChatHistoryRow.fromJson(_row('h-1', 'USER', 'Câu hỏi'));
      final answer = ChatHistoryRow.fromJson(_row('h-2', 'ASSISTANT', 'Trả lời'));
      expect(answeredInHistory([question, answer], 'Câu hỏi'), isTrue);
      expect(answeredInHistory([question], 'Câu hỏi'), isFalse);
      expect(answeredInHistory([question, answer], 'Câu khác'), isFalse);
    });

    test('failures map to the same customer messages', () {
      final options = RequestOptions(path: '/x');
      DioException status(int code) =>
          DioException(requestOptions: options, response: Response(requestOptions: options, statusCode: code));
      expect(chatFailure(DioException(requestOptions: options, type: DioExceptionType.receiveTimeout)).kind, ChatFailureKind.timeout);
      expect(chatFailure(DioException(requestOptions: options, type: DioExceptionType.connectionError)).ambiguous, isTrue);
      expect(chatFailure(status(409)).kind, ChatFailureKind.conflict);
      expect(chatFailure(status(401)).kind, ChatFailureKind.auth);
      expect(chatFailure(status(502)).text, contains('chưa được trả lời'));
    });
  });

  group('Chatbot screen flow', () {
    testWidgets('a new conversation shows starters, then a rich answer', (tester) async {
      final adapter = _Adapter((o) {
        if (o.path == '/api/auth/me') return _me;
        if (o.path == '/api/v1/chat/sessions') return <Object>[];
        if (o.method == 'POST' && o.path.endsWith('/messages')) return _reply;
        if (o.path.startsWith('/api/v1/chat/sessions/')) {
          return {'id': o.path.split('/')[5], 'customer_id': 'c', 'created_at': '2026-11-07T08:00:00Z', 'is_active': true};
        }
        return 404;
      });
      await tester.pumpWidget(_app(adapter));
      await tester.pumpAndSettle();

      expect(find.text('Bạn cần tư vấn gì hôm nay?'), findsOneWidget);
      // A fresh id cannot exist on the server: no session/history lookup before the first message.
      expect(adapter.requests.where((r) => r.path.contains('/messages')), isEmpty);

      await tester.tap(find.text('Tìm laptop dưới 20 triệu.'));
      await tester.pump();
      expect(find.widgetWithText(TextField, 'Tìm laptop dưới 20 triệu.'), findsOneWidget);

      await tester.tap(find.byTooltip('Gửi'));
      await tester.pumpAndSettle();

      expect(find.text('Mình gợi ý Dell Inspiron 15.'), findsOneWidget);
      expect(find.text('Tồn kho: Chưa xác định · Bảo hành 12 tháng'), findsOneWidget);
      expect(find.textContaining('So sánh thông số'), findsOneWidget);
      expect(find.text('Tốt hơn'), findsOneWidget);
      expect(find.text('Nguồn tham khảo'), findsOneWidget);
      expect(find.text('So sánh với Asus Vivobook 15'), findsOneWidget);
      expect(find.byTooltip('Kết thúc cuộc trò chuyện'), findsOneWidget);
    });

    testWidgets('a lost reply is reconciled from history and never resent automatically', (tester) async {
      var posts = 0;
      final adapter = _Adapter((o) {
        if (o.path == '/api/auth/me') return _me;
        if (o.path == '/api/v1/chat/sessions') return <Object>[];
        if (o.method == 'POST' && o.path.endsWith('/messages')) {
          posts += 1;
          return DioExceptionType.connectionError;
        }
        if (o.path.endsWith('/messages')) return [_row('h-1', 'USER', 'Giỏ hàng của tôi.')];
        return 404;
      });
      await tester.pumpWidget(_app(adapter));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Giỏ hàng của tôi.');
      await tester.pump();
      await tester.tap(find.byTooltip('Gửi'));
      await tester.pumpAndSettle();

      expect(posts, 1);
      expect(find.textContaining('Mất kết nối tới máy chủ'), findsOneWidget);
      expect(find.text('Đồng bộ lịch sử'), findsOneWidget);
      // The question is kept for the customer, but sending stays locked until history is synced.
      expect(find.widgetWithText(TextField, 'Giỏ hàng của tôi.'), findsOneWidget);
      expect(tester.widget<IconButton>(find.widgetWithIcon(IconButton, Icons.send)).onPressed, isNull);
    });

    testWidgets('a stored closed conversation reloads its history read-only', (tester) async {
      final adapter = _Adapter((o) {
        if (o.path == '/api/auth/me') return _me;
        if (o.path == '/api/v1/chat/sessions') {
          return [{'id': _sid, 'customer_id': 'c', 'created_at': '2026-11-07T08:00:00Z', 'is_active': false}];
        }
        if (o.path == '/api/v1/chat/sessions/$_sid') {
          return {'id': _sid, 'customer_id': 'c', 'created_at': '2026-11-07T08:00:00Z', 'is_active': false};
        }
        if (o.path == '/api/v1/chat/sessions/$_sid/messages') {
          return [_row('h-1', 'USER', 'Laptop 16GB'), _row('h-2', 'ASSISTANT', 'Mình gợi ý Dell Inspiron 15.')];
        }
        return 404;
      });
      await tester.pumpWidget(_app(adapter, stored: (userId: 'user-1', sessionId: _sid)));
      await tester.pumpAndSettle();

      expect(find.text('Laptop 16GB'), findsOneWidget);
      expect(find.text('Mình gợi ý Dell Inspiron 15.'), findsOneWidget);
      expect(find.textContaining('Cuộc trò chuyện này đã kết thúc'), findsOneWidget);
      expect(find.byTooltip('Kết thúc cuộc trò chuyện'), findsNothing);
    });
  });
}
