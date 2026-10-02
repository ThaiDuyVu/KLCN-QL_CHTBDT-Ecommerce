import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'auth_interceptor.dart';
import 'csrf_manager.dart';
import 'dart:convert';
import 'dart:io';
import '../storage/cookie_storage.dart';

class ApiClient {
  Dio get client => dio;
  static late final Dio dio;
  static final CsrfManager csrfManager = CsrfManager();

  static void init() {
    dio = Dio(BaseOptions(
      baseUrl: 'http://127.0.0.1:8081', 
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 10),
      
    ));

    dio.interceptors.add(CookieManager(CookieStorage.cookieJar));
    dio.interceptors.add(AuthInterceptor(dio, csrfManager));
  }

  static Future<void> ensureCsrfToken() async {
    try {
      print('--- Bắt đầu lấy CSRF Token ---');
      
      // NẾU CSRF TOKEN ĐÃ CÓ, KHÔNG PHẢI LẤY LẠI
      if (csrfManager.token != null && csrfManager.token!.isNotEmpty) {
        print('✅ CSRF Token đã tồn tại: ${csrfManager.token}');
        return;
      }
      
      final cleanDio = Dio(BaseOptions(
        baseUrl: 'http://127.0.0.1:8081',
        connectTimeout: const Duration(seconds: 10),
      ));
      
      // Khai báo lại balo chứa Cookie
      final cookieJar = CookieStorage.cookieJar;
      cleanDio.interceptors.add(CookieManager(cookieJar));

      // Gọi API 
      print('📤 Gửi request GET /api/auth/csrf...');
      await cleanDio.get('/api/auth/csrf');
      print('📥 Nhận response từ /api/auth/csrf');
      
      // --- TÌM COOKIE TRONG BALO ---
      List<Cookie> cookies = await cookieJar.loadForRequest(Uri.parse('http://127.0.0.1:8081'));
      
      String? extractedToken;
      for (var cookie in cookies) {
        print('🍪 Tìm thấy cookie: ${cookie.name} = ${cookie.value}');
        if (cookie.name == 'XSRF-TOKEN') {
          extractedToken = cookie.value;
          break;
        }
      }

      // Lưu Token vào Manager
      if (extractedToken != null && extractedToken.isNotEmpty) {
        csrfManager.token = extractedToken;
        print('✅ TÌM THẤY COOKIE XSRF-TOKEN: $extractedToken');
      } else {
        print('⚠️ Balo Cookie trống rỗng! Backend KHÔNG hề gửi Cookie về.');
        print('⚠️ DANH SÁCH COOKIE TRONG BALO:');
        for (var cookie in cookies) {
          print('   - ${cookie.name}: ${cookie.value}');
        }
        throw Exception('Backend không gửi XSRF-TOKEN cookie');
      }
      
    } catch (e) {
      print('❌ Lỗi mạng khi lấy CSRF: $e');
      throw Exception('Không thể lấy CSRF Token: $e'); 
    }
  }}