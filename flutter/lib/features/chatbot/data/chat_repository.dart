import 'dart:math';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ecommerce_app/core/network/api_client.dart';

import 'chat_models.dart';

export 'chat_models.dart';

/// Same chatbot service and endpoints as the web storefront (`chatbotApi.js`):
/// `/api/v1/chat/...` on [ApiClient.chatBaseUrl]. The shared Dio keeps cookies, CSRF and access headers.
class ChatRepository {
  /// The assistant may call the LLM and product search; the web allows 180 s as well.
  static const Duration replyTimeout = Duration(seconds: 180);
  static const int maxMessageLength = 4000; // SendMessageRequest.message max_length
  static const int sessionPageSize = 20;
  static const int historyPageSize = 100; // history_page default size
  final Dio client;
  final String _base;
  ChatRepository({Dio? client, String? baseUrl})
      : client = client ?? ApiClient.dio,
        _base = '${baseUrl ?? ApiClient.chatBaseUrl}/api/v1/chat';

  /// A new conversation id; the first message creates the session on the server.
  static String newSessionId() {
    final random = Random.secure();
    final bytes = List<int>.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
  }

  Future<List<ChatSessionInfo>> sessions({int page = 0}) async {
    final resp = await _call(() => client.get('$_base/sessions', queryParameters: {'page': page, 'size': sessionPageSize}));
    return _list(resp.data).map(ChatSessionInfo.fromJson).toList();
  }

  Future<ChatSessionInfo> session(String sessionId) async {
    final resp = await _call(() => client.get('$_base/sessions/$sessionId'));
    return ChatSessionInfo.fromJson(Map<String, dynamic>.from(resp.data as Map));
  }

  /// Latest page of the transcript, oldest first; [before] is a history id for older pages.
  Future<List<ChatHistoryRow>> history(String sessionId, {String? before}) async {
    final resp = await _call(() => client.get('$_base/sessions/$sessionId/messages',
        queryParameters: before == null ? null : {'before': before}));
    return _list(resp.data).map(ChatHistoryRow.fromJson).toList();
  }

  Future<ChatSessionInfo> close(String sessionId) async {
    final resp = await _call(() => client.post('$_base/sessions/$sessionId/close'));
    return ChatSessionInfo.fromJson(Map<String, dynamic>.from(resp.data as Map));
  }

  Future<ChatReply> send(String sessionId, String message) async {
    final resp = await _call(() => client.post(
          '$_base/sessions/$sessionId/messages',
          data: {'message': message},
          options: Options(receiveTimeout: replyTimeout, sendTimeout: const Duration(seconds: 30)),
        ));
    return ChatReply.fromJson(Map<String, dynamic>.from(resp.data as Map));
  }

  /// Logs where and how a call failed (no cookies or tokens): the screen only shows the customer message.
  static Future<Response<dynamic>> _call(Future<Response<dynamic>> Function() request) async {
    try {
      return await request();
    } on DioException catch (e) {
      debugPrint('[chatbot] ${e.requestOptions.method} ${e.requestOptions.uri} -> '
          '${e.response?.statusCode ?? e.type.name}${e.message == null ? '' : ' (${e.message})'}${e.error == null ? '' : ' ${e.error}'}');
      rethrow;
    }
  }

  static List<Map<String, dynamic>> _list(Object? data) =>
      data is List ? data.whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList() : const [];
}

final chatRepositoryProvider = Provider((ref) => ChatRepository());

/// Conversation selected per signed-in user, kept for the app's lifetime (the web keeps it per tab).
/// Transcripts are always reread from the server.
final chatSelectedSessionProvider = StateProvider<({String userId, String sessionId})?>((ref) => null);
