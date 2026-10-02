import 'package:dio/dio.dart';
import 'csrf_manager.dart';

class AuthInterceptor extends Interceptor {
  final Dio dio;
  final CsrfManager _csrfManager;
  // Biến lưu trữ token trong RAM để dùng ngay
  String? _inMemoryCsrfToken;

  AuthInterceptor(this.dio, this._csrfManager);

  @override
  Future<void> onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    print('🛡️ [Interceptor] Đang chặn request đi tới: ${options.path}');
    print('🛡️ [Interceptor] Method: ${options.method}');

    if (options.path == '/api/auth/csrf') {
      return handler.next(options);
    }

    final cookieHeader = options.headers['cookie']?.toString() ?? '';
    if (cookieHeader.contains('XSRF-TOKEN=') && !cookieHeader.contains('XSRF-TOKEN=;')) {
      final match = RegExp(r'XSRF-TOKEN=([^;]+)').firstMatch(cookieHeader);
      if (match != null) {
        _inMemoryCsrfToken = match.group(1);
      }
    }
    _inMemoryCsrfToken ??= _csrfManager.token;

    if (options.method != 'GET' && (_inMemoryCsrfToken == null || _inMemoryCsrfToken!.isEmpty)) {
      try {
        print('🛡️ [Interceptor] Thiếu CSRF cho request mutate. Đang tự động fetch...');
        final tempDio = Dio(BaseOptions(baseUrl: dio.options.baseUrl));
        final response = await tempDio.get('/api/auth/csrf');
        
        final setCookies = response.headers.map['set-cookie'] ?? [];
        for (var cookieStr in setCookies) {
          if (cookieStr.contains('XSRF-TOKEN=')) {
            final match = RegExp(r'XSRF-TOKEN=([^;]+)').firstMatch(cookieStr);
            if (match != null) {
              _inMemoryCsrfToken = match.group(1);
              _csrfManager.setToken(_inMemoryCsrfToken!); // Lưu lại luôn
              print('🛡️️ [Interceptor] Đã fetch thành công CSRF dự phòng: $_inMemoryCsrfToken');
              break;
            }
          }
        }
      } catch (e) {
        print('🛡️ [Interceptor] Lỗi khi tự động fetch CSRF: $e');
      }
    }

    String? accessToken;
    if (cookieHeader.contains('ACCESS_TOKEN=')) {
      final tokenMatch = RegExp(r'ACCESS_TOKEN=([^;]+)').firstMatch(cookieHeader);
      if (tokenMatch != null) {
        accessToken = tokenMatch.group(1);
        options.headers['Authorization'] = 'Bearer $accessToken';
      }
    }

    List<String> cleanCookies = [];
    if (accessToken != null) {
      cleanCookies.add('ACCESS_TOKEN=$accessToken');
    }
    
    if (_inMemoryCsrfToken != null && _inMemoryCsrfToken!.isNotEmpty) {
      cleanCookies.add('XSRF-TOKEN=$_inMemoryCsrfToken');
      // Chỉ gắn header cho các request có đổi dữ liệu
      if (options.method != 'GET') {
        options.headers['X-XSRF-TOKEN'] = _inMemoryCsrfToken;
      }
    }
    
    if (cleanCookies.isNotEmpty) {
      options.headers['cookie'] = cleanCookies.join('; ');
    }

    print('🛡️ [Interceptor] -> Headers gửi đi cuối cùng:');
    options.headers.forEach((key, value) {
      print('🛡️              - $key: $value');
    });

    return handler.next(options);
  }}