import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../auth/presentation/providers/auth_provider.dart';
import '../../products/presentation/widgets/shop_widgets.dart';
import '../data/chat_logic.dart';
import '../data/chat_repository.dart';
import 'chat_widgets.dart';

// Starter prompts are UI shortcuts; they fill the input and are not backend suggestions (same list as the web).
const _starters = ['Tìm laptop dưới 20 triệu.', 'Tư vấn điện thoại RAM 8GB.', 'Tìm sản phẩm phù hợp cho học tập.', 'Đơn hàng gần đây của tôi.', 'Giỏ hàng của tôi.'];

/// Customer assistant on mobile: same chatbot service and the same flow as the web `/chatbot` page
/// (stored conversations, history, close, reconcile after ambiguous failures, product cards, comparison, sources).
class ChatbotScreen extends ConsumerWidget {
  const ChatbotScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final userId = ref.watch(authProvider.select((state) => state.user?['userId']))?.toString() ?? '';
    // Wait for the account: a workspace without one would overwrite the remembered conversation.
    if (userId.isEmpty) {
      return Scaffold(appBar: AppBar(title: const Text('Trợ lý mua sắm Điện Việt')), body: const Center(child: CircularProgressIndicator()));
    }
    // Keyed by user: switching accounts discards every in-memory conversation.
    return _ChatWorkspace(key: ValueKey(userId), userId: userId);
  }
}

class _ChatWorkspace extends ConsumerStatefulWidget {
  final String userId;
  const _ChatWorkspace({super.key, required this.userId});

  @override
  ConsumerState<_ChatWorkspace> createState() => _ChatWorkspaceState();
}

class _ChatWorkspaceState extends ConsumerState<_ChatWorkspace> {
  final _input = TextEditingController();
  final _scroll = ScrollController();
  final _focus = FocusNode();

  late String _sessionId;
  /// Id generated here and not sent yet: it cannot exist on the server, so lookups are skipped.
  String? _fresh;
  ChatSessionInfo? _session; // null until the first message creates it
  List<ChatSessionInfo> _sessions = [];
  int _sessionPage = 0;
  bool _moreSessions = false, _loadingSessions = true;
  ChatFailure? _sessionsError;
  List<ChatItem> _messages = [];
  bool _hasOlder = false, _loading = true, _busy = false, _needsSync = false, _closing = false;
  ChatFailure? _error;
  String _notice = '';
  /// Bumped on every conversation switch; late results of an older conversation are discarded.
  int _version = 0;
  int? _inFlight;

  ChatRepository get _repo => ref.read(chatRepositoryProvider);
  bool get _closed => _session?.isActive == false;
  bool get _composerLocked => _busy || _loading || _closed || _needsSync;
  bool _current(int token) => mounted && token == _version;

  @override
  void initState() {
    super.initState();
    final stored = ref.read(chatSelectedSessionProvider);
    if (stored != null && stored.userId == widget.userId) {
      _sessionId = stored.sessionId;
    } else {
      _sessionId = ChatRepository.newSessionId();
      _fresh = _sessionId;
    }
    _input.addListener(() => setState(() {}));
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _remember();
      _loadSessions();
      _loadConversation();
    });
  }

  @override
  void dispose() {
    _input.dispose();
    _scroll.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _remember() => ref.read(chatSelectedSessionProvider.notifier).state = (userId: widget.userId, sessionId: _sessionId);

  void _scrollToEnd() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) {
        _scroll.animateTo(_scroll.position.maxScrollExtent, duration: const Duration(milliseconds: 250), curve: Curves.easeOut);
      }
    });
  }

  static List<ChatSessionInfo> _mergeSessions(List<ChatSessionInfo> first, List<ChatSessionInfo> second) =>
      [...first, ...second.where((row) => !first.any((item) => item.id == row.id))];

  Future<void> _loadSessions() async {
    try {
      final rows = await _repo.sessions();
      if (!mounted) return;
      setState(() {
        _sessions = rows;
        _sessionPage = 0;
        _moreSessions = rows.length == ChatRepository.sessionPageSize;
      });
    } catch (failure) {
      if (mounted) setState(() => _sessionsError = chatFailure(failure));
    } finally {
      if (mounted) setState(() => _loadingSessions = false);
    }
  }

  Future<void> _olderSessions() async {
    if (_loadingSessions) return;
    final next = _sessionPage + 1;
    setState(() => _loadingSessions = true);
    try {
      final rows = await _repo.sessions(page: next);
      if (!mounted) return;
      setState(() {
        _sessions = _mergeSessions(_sessions, rows);
        _sessionPage = next;
        _moreSessions = rows.length == ChatRepository.sessionPageSize;
        _sessionsError = null;
      });
    } catch (failure) {
      if (mounted) setState(() => _sessionsError = chatFailure(failure));
    } finally {
      if (mounted) setState(() => _loadingSessions = false);
    }
  }

  void _refreshSessions() {
    final token = _version;
    _repo.sessions().then((rows) {
      if (_current(token)) setState(() { _sessions = _mergeSessions(rows, _sessions); _sessionsError = null; });
    }).catchError((_) {/* The list refresh is cosmetic; the conversation itself already succeeded. */});
  }

  Future<void> _loadConversation() async {
    final token = _version;
    if (_fresh == _sessionId) {
      setState(() => _loading = false);
      return;
    }
    final sid = _sessionId;
    // Run both lookups; 404 is the normal state of a conversation without its first message.
    final results = await Future.wait([
      _repo.session(sid).then<Object>((value) => value, onError: (Object e) => e),
      _repo.history(sid).then<Object>((value) => value, onError: (Object e) => e),
    ]);
    if (!_current(token)) return;
    setState(() {
      final record = results[0], history = results[1];
      if (record is ChatSessionInfo) {
        _session = record;
      } else if (statusOf(record) == 404) {
        _session = null;
      } else {
        _error = chatFailure(record);
      }
      if (history is List<ChatHistoryRow>) {
        _messages = mergeHistory(history);
        _hasOlder = history.length == ChatRepository.historyPageSize;
      } else if (statusOf(history) != 404) {
        _error = chatFailure(history);
      }
      _loading = false;
    });
    _scrollToEnd();
  }

  void _switchTo(String id, [ChatSessionInfo? record]) {
    setState(() {
      _version += 1;
      _inFlight = null;
      _sessionId = id;
      _session = record;
      _messages = [];
      _hasOlder = false;
      _loading = true;
      _busy = false;
      _error = null;
      _notice = '';
      _needsSync = false;
      _closing = false;
    });
    _remember();
    _loadConversation();
  }

  void _startNew() {
    final id = ChatRepository.newSessionId();
    _fresh = id;
    _switchTo(id);
  }

  Future<void> _send() async {
    final question = _input.text.trim();
    if (question.isEmpty || question.length > ChatRepository.maxMessageLength || _composerLocked || _inFlight != null) return;
    final token = _version, sid = _sessionId, pendingId = ChatRepository.newSessionId();
    _inFlight = token;
    if (_fresh == sid) _fresh = null; // the POST may create it from here on
    setState(() {
      _busy = true;
      _error = null;
      _notice = '';
      _input.clear();
      _messages = [..._messages, ChatItem(id: pendingId, fromAssistant: false, text: question, createdAt: DateTime.now(), pending: true)];
    });
    _scrollToEnd();
    try {
      final reply = await _repo.send(sid, question);
      if (!_current(token)) return;
      setState(() {
        _messages = [
          for (final item in _messages) item.id == pendingId ? item.confirmed() : item,
          ChatItem(
            id: ChatRepository.newSessionId(),
            fromAssistant: true,
            text: reply.message,
            createdAt: DateTime.now(),
            products: reply.products,
            sources: reply.sources,
            suggestions: reply.suggestions,
            comparison: reply.comparison,
          ),
        ];
      });
      if (_session == null) {
        _repo.session(sid).then((record) { if (_current(token)) setState(() => _session = record); }).catchError((_) {});
      }
      _refreshSessions();
    } catch (failure) {
      if (!_current(token)) return;
      final error = chatFailure(failure);
      setState(() {
        _error = error;
        _input.text = question;
      });
      // The POST may have been processed. Reconcile from history; never replay the request automatically.
      try {
        final rows = await _repo.history(sid);
        if (!_current(token)) return;
        final completed = error.ambiguous && answeredInHistory(rows, question);
        setState(() {
          _messages = mergeHistory(rows, _messages);
          _hasOlder = rows.length == ChatRepository.historyPageSize;
          _needsSync = error.ambiguous && !completed;
          if (completed) {
            _error = null;
            _input.clear();
            _notice = 'Câu trả lời đã được lưu trên máy chủ và hiển thị theo lịch sử bên dưới.';
          }
        });
      } catch (historyFailure) {
        if (!_current(token)) return;
        setState(() {
          // A returned HTTP error plus 404 history means the conversation was never created.
          if (statusOf(historyFailure) == 404 && !error.ambiguous) {
            _messages = _messages.where((item) => item.id != pendingId).toList();
          } else {
            _needsSync = true;
          }
        });
      }
      if (statusOf(failure) == 409) {
        _repo.session(sid).then((record) { if (_current(token)) setState(() => _session = record); }).catchError((_) {});
      }
    } finally {
      if (_inFlight == token) _inFlight = null;
      if (_current(token)) setState(() => _busy = false);
      _scrollToEnd();
    }
  }

  Future<void> _syncHistory() async {
    final token = _version;
    setState(() { _loading = true; _error = null; });
    try {
      final rows = await _repo.history(_sessionId);
      final record = await _repo.session(_sessionId).then<ChatSessionInfo?>((value) => value, onError: (_) => _session);
      if (!_current(token)) return;
      setState(() {
        _messages = mergeHistory(rows, _messages);
        _hasOlder = rows.length == ChatRepository.historyPageSize;
        _session = record;
        _needsSync = false;
      });
    } catch (failure) {
      if (!_current(token)) return;
      setState(() {
        if (statusOf(failure) == 404) {
          _messages = [];
          _needsSync = false;
        } else {
          _error = chatFailure(failure);
        }
      });
    } finally {
      if (_current(token)) setState(() => _loading = false);
    }
  }

  Future<void> _olderHistory() async {
    final cursor = _messages.where((item) => item.serverId != null).firstOrNull?.serverId;
    if (cursor == null || _loading || _busy) return;
    final token = _version;
    setState(() { _loading = true; _error = null; });
    try {
      final rows = await _repo.history(_sessionId, before: cursor);
      if (!_current(token)) return;
      setState(() {
        _messages = [...mergeHistory(rows).where((row) => !_messages.any((item) => item.id == row.id)), ..._messages];
        _hasOlder = rows.length == ChatRepository.historyPageSize;
      });
    } catch (failure) {
      if (_current(token)) setState(() => _error = chatFailure(failure));
    } finally {
      if (_current(token)) setState(() => _loading = false);
    }
  }

  Future<void> _confirmClose() async {
    final yes = await showDialog<bool>(
      context: context,
      builder: (dialog) => AlertDialog(
        title: const Text('Kết thúc cuộc trò chuyện này?'),
        content: const Text('Bạn vẫn đọc lại được lịch sử, nhưng không gửi thêm tin nhắn vào cuộc trò chuyện này.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialog, false), child: const Text('Giữ lại')),
          FilledButton(onPressed: () => Navigator.pop(dialog, true), child: const Text('Kết thúc')),
        ],
      ),
    );
    if (yes != true || !mounted) return;
    final token = _version;
    setState(() { _closing = true; _error = null; });
    try {
      final record = await _repo.close(_sessionId);
      if (!_current(token)) return;
      setState(() {
        _session = record;
        _sessions = [for (final item in _sessions) item.id == record.id ? record : item];
      });
    } catch (failure) {
      if (_current(token)) setState(() => _error = chatFailure(failure));
    } finally {
      if (_current(token)) setState(() => _closing = false);
    }
  }

  void _applySuggestion(String question) {
    _input.text = question;
    _input.selection = TextSelection.collapsed(offset: question.length);
    _focus.requestFocus();
  }

  Widget _banner(String text, {Color? color, Widget? action}) => Container(
        width: double.infinity,
        margin: const EdgeInsets.fromLTRB(12, 4, 12, 0),
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(color: color ?? shopTint, borderRadius: BorderRadius.circular(8)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(text), ?action]),
      );

  Widget _sessionDrawer() {
    final inList = _sessions.any((item) => item.id == _sessionId);
    Widget tile(String title, String subtitle, {required bool active, VoidCallback? onTap}) => ListTile(
          selected: active,
          selectedTileColor: shopTint,
          title: Text(title, style: const TextStyle(fontSize: 14)),
          subtitle: Text(subtitle),
          onTap: onTap,
        );
    return Drawer(
      child: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(12),
              child: SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  icon: const Icon(Icons.add_comment_outlined),
                  label: const Text('Cuộc trò chuyện mới'),
                  onPressed: _busy ? null : () { Navigator.pop(context); _startNew(); },
                ),
              ),
            ),
            if (_sessionsError != null) Padding(padding: const EdgeInsets.all(12), child: Text(_sessionsError!.text, style: const TextStyle(color: Colors.red))),
            Expanded(
              child: ListView(
                children: [
                  if (!inList)
                    tile('Cuộc trò chuyện mới', _session == null ? 'Chưa gửi tin nhắn' : (_closed ? 'Đã kết thúc' : 'Đang mở'), active: true),
                  for (final item in _sessions)
                    tile(sessionLabel(item), item.isActive ? 'Đang mở' : 'Đã kết thúc',
                        active: item.id == _sessionId,
                        onTap: item.id == _sessionId ? null : () { Navigator.pop(context); _switchTo(item.id, item); }),
                  if (_loadingSessions) const Padding(padding: EdgeInsets.all(12), child: Text('Đang tải danh sách…')),
                  if (!_loadingSessions && _sessions.isEmpty && _sessionsError == null)
                    const Padding(padding: EdgeInsets.all(12), child: Text('Chưa có cuộc trò chuyện nào được lưu.')),
                  if (_moreSessions)
                    TextButton(onPressed: _loadingSessions ? null : _olderSessions, child: const Text('Tải thêm cuộc trò chuyện')),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _empty() => Padding(
        padding: const EdgeInsets.all(8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Bạn cần tư vấn gì hôm nay?', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700, color: shopInk)),
            const SizedBox(height: 6),
            const Text('Hỏi về sản phẩm, ngân sách, cấu hình hoặc tra cứu đơn hàng, giỏ hàng của bạn. '
                'Trợ lý chỉ tư vấn và tra cứu, không đặt hàng hay thanh toán thay bạn.'),
            const SizedBox(height: 8),
            Text('Bạn có thể bắt đầu với:', style: TextStyle(color: Colors.grey.shade700)),
            Wrap(
              spacing: 6,
              children: [
                for (final question in _starters)
                  ActionChip(label: Text(question, style: const TextStyle(fontSize: 12)), onPressed: _composerLocked ? null : () => _applySuggestion(question)),
              ],
            ),
          ],
        ),
      );

  @override
  Widget build(BuildContext context) {
    final inList = _sessions.where((item) => item.id == _sessionId).firstOrNull;
    final tooLong = _input.text.trim().length > ChatRepository.maxMessageLength;
    final status = _closed
        ? 'Đã kết thúc · chỉ xem lại lịch sử'
        : _session != null
            ? 'Đang mở'
            : 'Được lưu khi bạn gửi tin nhắn đầu tiên';
    return Scaffold(
      endDrawer: _sessionDrawer(),
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(inList != null ? sessionLabel(inList) : 'Trợ lý mua sắm Điện Việt', style: const TextStyle(fontSize: 16)),
            Text(status, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.normal)),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Cuộc trò chuyện mới',
            icon: const Icon(Icons.add_comment_outlined),
            onPressed: _busy ? null : _startNew,
          ),
          if (_session != null && !_closed)
            IconButton(
              tooltip: 'Kết thúc cuộc trò chuyện',
              icon: const Icon(Icons.stop_circle_outlined),
              onPressed: _busy || _loading || _closing ? null : _confirmClose,
            ),
          Builder(
            builder: (context) => IconButton(
              tooltip: 'Danh sách trò chuyện',
              icon: const Icon(Icons.forum_outlined),
              onPressed: () => Scaffold.of(context).openEndDrawer(),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView(
              controller: _scroll,
              padding: const EdgeInsets.all(12),
              children: [
                if (_hasOlder)
                  TextButton(onPressed: _loading || _busy ? null : _olderHistory, child: const Text('Xem tin nhắn cũ hơn')),
                if (_loading && _messages.isEmpty) const Padding(padding: EdgeInsets.all(8), child: Text('Đang tải lịch sử trò chuyện…')),
                if (!_loading && _messages.isEmpty) _empty(),
                for (final item in _messages)
                  ChatBubble(key: ValueKey(item.id), item: item, onSuggestion: _applySuggestion, suggestionsDisabled: _composerLocked),
                if (_busy) const Padding(padding: EdgeInsets.all(8), child: Text('Đang trả lời…', style: TextStyle(color: Colors.blueGrey))),
              ],
            ),
          ),
          if (_notice.isNotEmpty) _banner(_notice),
          if (_error != null)
            _banner(
              _error!.text,
              color: Colors.red.shade50,
              action: _error!.kind == ChatFailureKind.auth
                  ? TextButton(onPressed: () => ref.read(authProvider.notifier).logout(), child: const Text('Đăng nhập lại'))
                  : null,
            ),
          if (_needsSync)
            _banner(
              'Chưa xác minh được câu hỏi vừa gửi. Đồng bộ lịch sử trước khi gửi lại để tránh hỏi trùng.',
              color: Colors.amber.shade50,
              action: TextButton(onPressed: _loading ? null : _syncHistory, child: const Text('Đồng bộ lịch sử')),
            ),
          if (_closed)
            _banner(
              'Cuộc trò chuyện này đã kết thúc. Bạn vẫn có thể đọc lại lịch sử.',
              action: TextButton(onPressed: _startNew, child: const Text('Bắt đầu cuộc trò chuyện mới')),
            ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 6, 12, 6),
              child: Column(
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _input,
                          focusNode: _focus,
                          enabled: !_closed,
                          maxLength: ChatRepository.maxMessageLength,
                          minLines: 1,
                          maxLines: 4,
                          textInputAction: TextInputAction.newline,
                          decoration: InputDecoration(
                            hintText: _closed ? 'Cuộc trò chuyện đã kết thúc' : 'Nhập câu hỏi của bạn…',
                            counterText: '${_input.text.length}/${ChatRepository.maxMessageLength}',
                            border: const OutlineInputBorder(),
                            isDense: true,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Padding(
                        padding: const EdgeInsets.only(bottom: 20),
                        child: IconButton.filled(
                          tooltip: 'Gửi',
                          onPressed: _composerLocked || _input.text.trim().isEmpty || tooLong ? null : _send,
                          icon: _busy
                              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                              : const Icon(Icons.send),
                        ),
                      ),
                    ],
                  ),
                  Text(
                    'Giá và tồn kho trong câu trả lời được lấy từ hệ thống tại thời điểm hỏi và có thể khác chi nhánh bạn đang chọn. '
                    'Vui lòng kiểm tra lại ở trang chi tiết sản phẩm trước khi mua.',
                    style: TextStyle(fontSize: 11, color: Colors.grey.shade600),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
