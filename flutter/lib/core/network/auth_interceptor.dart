import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';

/// Uses the same jar as CookieManager, preserving scoped refresh cookies.
class AuthInterceptor extends Interceptor {
  final CookieJar cookies;
  final Future<String> Function() ensureCsrfToken;
  AuthInterceptor(this.cookies, this.ensureCsrfToken);

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    if (['GET', 'HEAD', 'OPTIONS'].contains(options.method.toUpperCase())) {
      handler.next(options);
      return;
    }
    try {
      final token = await ensureCsrfToken();
      // CookieManager already ran; reload after CSRF initialization to include
      // the newly issued cookie, along with every cookie matching this URI.
      final values = await cookies.loadForRequest(options.uri);
      options.headers['cookie'] = values
          .map((c) => '${c.name}=${c.value}')
          .join('; ');
      options.headers['X-XSRF-TOKEN'] = token;
      handler.next(options);
    } catch (error) {
      handler.reject(
        DioException(
          requestOptions: options,
          error: error,
          message: 'Không lấy được CSRF. Thao tác chưa được gửi.',
          type: DioExceptionType.unknown,
        ),
      );
    }
  }
}
