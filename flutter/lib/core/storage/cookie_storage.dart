import 'dart:io';
import 'package:cookie_jar/cookie_jar.dart';
import 'package:path_provider/path_provider.dart';

class CookieStorage {
  static CookieJar? _cookieJar;

  static CookieJar get cookieJar => _cookieJar ??= CookieJar();
  static set cookieJar(CookieJar jar) => _cookieJar = jar;

  static Future<void> init() async {
    Directory appDocDir = await getApplicationDocumentsDirectory();
    String appDocPath = appDocDir.path;

    // Lưu cookie vào thư mục /.cookies
    cookieJar = PersistCookieJar(
      ignoreExpires: false, // Không gửi access cookie đã hết hạn
      storage: FileStorage("$appDocPath/.cookies/"),
    );
  }

  // Hàm tiện ích để xóa sạch cookie (dùng khi Logout hoặc Refresh thất bại)
  static Future<void> clearCookies() async {
    await cookieJar.deleteAll();
  }
}
