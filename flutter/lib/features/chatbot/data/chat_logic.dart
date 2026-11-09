// Pure helpers for the assistant screen, ported from the web (`customerChat.js`); no network or widget state.
import 'package:dio/dio.dart';

import 'chat_repository.dart';

enum ChatFailureKind { timeout, network, auth, forbidden, missing, conflict, invalid, unavailable }

class ChatFailure {
  final ChatFailureKind kind;
  final String text;
  const ChatFailure(this.kind, this.text);

  /// The request may still have been processed: reconcile from history, never replay it.
  bool get ambiguous => kind == ChatFailureKind.timeout || kind == ChatFailureKind.network;
}

int? statusOf(Object error) => error is DioException ? error.response?.statusCode : null;

ChatFailure chatFailure(Object error) {
  if (error is DioException &&
      (error.type == DioExceptionType.receiveTimeout || error.type == DioExceptionType.sendTimeout)) {
    return const ChatFailure(ChatFailureKind.timeout,
        'Trợ lý cần nhiều thời gian hơn dự kiến. Câu trả lời có thể vẫn đang được xử lý; hãy đồng bộ lịch sử trước khi gửi lại.');
  }
  final status = statusOf(error);
  if (status == null) {
    return const ChatFailure(ChatFailureKind.network,
        'Mất kết nối tới máy chủ. Câu hỏi có thể đã được gửi; hãy đồng bộ lịch sử trước khi gửi lại.');
  }
  if (status == 401) {
    return const ChatFailure(ChatFailureKind.auth, 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để tiếp tục trò chuyện.');
  }
  if (status == 403) {
    return const ChatFailure(ChatFailureKind.forbidden, 'Tài khoản của bạn không có quyền truy cập cuộc trò chuyện này.');
  }
  if (status == 404) {
    return const ChatFailure(ChatFailureKind.missing, 'Cuộc trò chuyện không tồn tại hoặc không còn truy cập được.');
  }
  if (status == 409) {
    return const ChatFailure(ChatFailureKind.conflict,
        'Cuộc trò chuyện đang xử lý một câu hỏi khác hoặc đã kết thúc. Hãy đồng bộ lịch sử rồi thử lại.');
  }
  if (status == 400 || status == 422) {
    return const ChatFailure(ChatFailureKind.invalid,
        'Câu hỏi chưa hợp lệ. Vui lòng nhập tối đa ${ChatRepository.maxMessageLength} ký tự.');
  }
  // Upstream detail texts are operator-facing; never tell the customer an answer exists.
  return const ChatFailure(ChatFailureKind.unavailable,
      'Trợ lý AI tạm thời chưa khả dụng. Câu hỏi này chưa được trả lời; vui lòng thử lại sau.');
}

/// History is text-only. Reattach cards/sources/comparison only to the same assistant text seen live on this screen.
List<ChatItem> mergeHistory(List<ChatHistoryRow> rows, [List<ChatItem> current = const []]) {
  final rich = current.where((item) => item.fromAssistant && item.hasExtras).toList();
  return rows.map((row) {
    final index = row.fromAssistant ? rich.indexWhere((item) => item.text == row.message) : -1;
    final extra = index >= 0 ? rich.removeAt(index) : null;
    return ChatItem(
      id: row.id,
      serverId: row.id,
      fromAssistant: row.fromAssistant,
      text: row.message,
      createdAt: row.createdAt,
      products: extra?.products ?? const [],
      sources: extra?.sources ?? const [],
      suggestions: extra?.suggestions ?? const [],
      comparison: extra?.comparison,
    );
  }).toList();
}

/// After an ambiguous failure, the turn is complete only if history ends with this question and an answer.
bool answeredInHistory(List<ChatHistoryRow> rows, String question) {
  if (rows.length < 2) return false;
  final user = rows[rows.length - 2], assistant = rows.last;
  return user.senderType == 'USER' && user.message == question && assistant.fromAssistant;
}

String _two(int value) => value.toString().padLeft(2, '0');

String messageTime(DateTime? value) => value == null ? '' : '${_two(value.hour)}:${_two(value.minute)}';

/// Sessions have no stored title; label them by their real creation time only.
String sessionLabel(ChatSessionInfo? session) {
  final created = session?.createdAt;
  if (created == null) return 'Cuộc trò chuyện';
  return 'Cuộc trò chuyện · ${_two(created.day)}/${_two(created.month)}/${created.year} ${messageTime(created)}';
}

/// Only http(s) links from knowledge sources are opened.
Uri? safeHttpUrl(String? value) {
  final uri = value == null ? null : Uri.tryParse(value);
  return uri != null && (uri.scheme == 'http' || uri.scheme == 'https') && uri.host.isNotEmpty ? uri : null;
}
