import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../data/auth_repository.dart';

class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  final _usernameController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  
  bool _isLoading = false;
  final AuthRepository _authRepository = AuthRepository();

  Future<void> _handleRegister() async {
    // Validate cơ bản
    if (_usernameController.text.isEmpty || _passwordController.text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Vui lòng nhập đầy đủ thông tin')),
      );
      return;
    }

    setState(() => _isLoading = true);

    try {
      print('📝 [RegisterScreen] Bắt đầu quá trình đăng ký');
      
      // Gọi hàm register từ Repository
      final response = await _authRepository.register(
        _usernameController.text,
        _emailController.text,
        _passwordController.text,
      );
      
      print('📝 [RegisterScreen] Response status: ${response.statusCode}');
      print('📝 [RegisterScreen] Response data: ${response.data}');
      
      // Thành công: Báo Toast và chuyển về màn hình Login
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('✅ Đăng ký thành công! Hãy đăng nhập.')),
        );
        context.go('/login'); 
      }
    } catch (e) {
      print('📝 [RegisterScreen] ❌ LỖI: $e');
      print('📝 [RegisterScreen] Exception type: ${e.runtimeType}');
      
      // Hiển thị lỗi chi tiết
      if (mounted) {
        String errorMessage = 'Lỗi đăng ký';
        
        // Nếu là DioException, lấy chi tiết lỗi từ Backend
        if (e.toString().contains('DioException')) {
          if (e.toString().contains('401')) {
            errorMessage = '❌ 401 Unauthorized - Thiếu CSRF Token hoặc Token không hợp lệ';
          } else if (e.toString().contains('403')) {
            errorMessage = '❌ 403 Forbidden - CSRF Token bị từ chối';
          } else if (e.toString().contains('400')) {
            errorMessage = '❌ 400 Bad Request - Dữ liệu không hợp lệ';
          } else if (e.toString().contains('Không thể lấy CSRF Token')) {
            errorMessage = '❌ Không thể lấy CSRF Token từ Backend - Kiểm tra kết nối server';
          } else {
            errorMessage = '❌ Lỗi: ${e.toString().substring(0, 100)}';
          }
        }
        
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(errorMessage),
            duration: const Duration(seconds: 5),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  void dispose() {
    _usernameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Đăng ký tài khoản')),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: _usernameController,
              decoration: const InputDecoration(
                labelText: 'Tên đăng nhập',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _emailController,
              decoration: const InputDecoration(
                labelText: 'Email',
                border: OutlineInputBorder(),
              ),
              keyboardType: TextInputType.emailAddress,
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _passwordController,
              decoration: const InputDecoration(
                labelText: 'Mật khẩu',
                border: OutlineInputBorder(),
              ),
              obscureText: true,
            ),
            const SizedBox(height: 24),
            
            // Hiện vòng xoay nếu đang gọi API, ngược lại hiện nút bấm
            _isLoading 
              ? const Center(child: CircularProgressIndicator())
              : ElevatedButton(
                  onPressed: _handleRegister,
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                  ),
                  child: const Text('ĐĂNG KÝ', style: TextStyle(fontSize: 16)),
                ),
                
            const SizedBox(height: 16),
            TextButton(
              onPressed: () {
                // Nếu đổi ý không đăng ký nữa thì back về Login
                if (context.canPop()) {
                  context.pop();
                } else {
                  context.go('/login');
                }
              },
              child: const Text('Đã có tài khoản? Đăng nhập'),
            ),
          ],
        ),
      ),
    );
  }
}