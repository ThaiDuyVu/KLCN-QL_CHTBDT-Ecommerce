import 'package:dio/dio.dart';

String apiErrorMessage(Object error) {
  if (error is! DioException) return 'Thao tác thất bại. Vui lòng thử lại.';
  final status = error.response?.statusCode;
  if (status == 401) return 'Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.';
  if (status == 403)
    return 'Yêu cầu bị từ chối. Kiểm tra quyền tài khoản hoặc phiên bảo mật.';
  final data = error.response?.data;
  if (data is String && data.isNotEmpty && !data.trimLeft().startsWith('<'))
    return data;
  if (data is Map && data['message'] is String)
    return data['message'] as String;
  if (status == 400)
    return 'Thông tin gửi chưa hợp lệ. Kiểm tra các trường bắt buộc.';
  if (status == 405) return 'Thao tác không được API hỗ trợ.';
  if (status == 409)
    return 'Giỏ hàng hoặc tồn kho đã thay đổi. Vui lòng tải lại.';
  if (error.message == 'Không lấy được CSRF. Thao tác chưa được gửi.')
    return error.message!;
  if (status == null)
    return 'Không nhận được phản hồi. Kiểm tra kết nối; nếu đang đặt hàng, hãy xem đơn hàng trước khi thử lại.';
  return 'Yêu cầu thất bại ($status).';
}
