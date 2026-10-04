import 'package:dio/dio.dart';
import 'dart:convert';
import '../../../core/network/api_client.dart';

/// Repository xử lý các API liên quan authentication
class AuthRepository {
  final Dio client = ApiClient.dio;

  /// Đảm bảo có CSRF token trước khi login/logout
  Future<Response> login(String username, String password) async {
    print('🔑 [Login] Bắt đầu quá trình đăng nhập');

    // 🔐 BƯỚC 1: ĐẢM BẢO LẤY ĐƯỢC CSRF TOKEN
    try {
      print('🔑 [Login] 1️⃣ Gọi ensureCsrfToken()...');
      await ApiClient.ensureCsrfToken();
      print('🔑 [Login] ✅ Đã lấy được CSRF Token');
    } catch (e) {
      print('🔑 [Login] ❌ LỖI LẤY CSRF TOKEN: $e');
      rethrow;
    }

    // 🔐 BƯỚC 2: KIỂM TRA CSRF TOKEN CÓ ĐƯỢC THIẾT LẬP KHÔNG
    if (ApiClient.csrfManager.token == null) {
      print(
        '🔑 [Login] ⚠️ CẢNH BÁO: CSRF Token vẫn NULL sau ensureCsrfToken()!',
      );
    } else {
      print('🔑 [Login] CSRF đã sẵn sàng');
    }

    // 🔐 BƯỚC 3: GỬI REQUEST LOGIN
    try {
      print('🔑 [Login] 2️⃣ Gửi request POST /api/auth/login...');
      return await client.post(
        '/api/auth/login',
        // 1. Ép kiểu dữ liệu thành chuỗi JSON chuẩn mực
        data: jsonEncode({'username': username, 'password': password}),
        // 2. Bắt buộc khai báo "Tôi đang gửi JSON nhé!"
        options: Options(
          headers: {'Content-Type': 'application/json; charset=UTF-8'},
        ),
      );
    } catch (e) {
      print('🔑 [Login] ❌ LỖI KHI GỬI REQUEST: $e');
      rethrow;
    }
  }

  Future<Response> register(
    String username,
    String email,
    String password,
  ) async {
    print('📝 [Register] Bắt đầu quá trình đăng ký');

    // 🔐 BƯỚC 1: ĐẢM BẢO LẤY ĐƯỢC CSRF TOKEN
    try {
      print('📝 [Register] 1️⃣ Gọi ensureCsrfToken()...');
      await ApiClient.ensureCsrfToken();
      print('📝 [Register] ✅ Đã lấy được CSRF Token');
    } catch (e) {
      print('📝 [Register] ❌ LỖI LẤY CSRF TOKEN: $e');
      rethrow; // Ném lỗi ra ngoài nếu không lấy được CSRF
    }

    // 🔐 BƯỚC 2: KIỂM TRA CSRF TOKEN CÓ ĐƯỢC THIẾT LẬP KHÔNG
    if (ApiClient.csrfManager.token == null) {
      print(
        '📝 [Register] ⚠️ CẢNH BÁO: CSRF Token vẫn NULL sau ensureCsrfToken()!',
      );
    } else {
      print(
        '📝 [Register] CSRF đã sẵn sàng',
      );
    }

    // 🔐 BƯỚC 3: GỬI REQUEST REGISTER VỚI JSON ĐÚNG FORMAT
    try {
      print('📝 [Register] 2️⃣ Gửi request POST /api/auth/register...');
      final Response response = await client.post(
        '/api/auth/register',
        // 1. Ép kiểu dữ liệu thành chuỗi JSON chuẩn mực (GIỐNG login())
        data: jsonEncode({
          'username': username,
          'email': email,
          'password': password,
        }),
        // 2. Bắt buộc khai báo "Tôi đang gửi JSON!"
        options: Options(
          headers: {'Content-Type': 'application/json; charset=UTF-8'},
        ),
      );

      print(
        '📝 [Register] ✅ Đăng ký thành công! StatusCode: ${response.statusCode}',
      );
      return response;
    } catch (e) {
      print('📝 [Register] ❌ LỖI KHI GỬI REQUEST: $e');
      rethrow;
    }
  }

  Future<Response> logout() async {
    await ApiClient.ensureCsrfToken();
    return await client.post('/api/auth/logout');
  }

  Future<Response> getMe() async {
    return await client.get('/api/auth/me');
  }
}
