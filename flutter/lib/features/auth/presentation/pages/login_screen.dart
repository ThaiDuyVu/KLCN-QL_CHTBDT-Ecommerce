import 'package:flutter/material.dart';
import 'package:dio/dio.dart';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart'; 
import '../../presentation/providers/auth_provider.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _usernameCtrl = TextEditingController();
  final _passwordCtrl = TextEditingController();
  bool _loading = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Đăng nhập')),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Form(
          key: _formKey,
          child: Column(
            children: [
              TextFormField(
                controller: _usernameCtrl,
                decoration: const InputDecoration(labelText: 'Tài khoản'),
                validator: (v) => (v == null || v.isEmpty) ? 'Nhập tài khoản' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _passwordCtrl,
                decoration: const InputDecoration(labelText: 'Mật khẩu'),
                obscureText: true,
                validator: (v) => (v == null || v.isEmpty) ? 'Nhập mật khẩu' : null,
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: _loading ? null : _onSubmit,
                child: _loading ? const CircularProgressIndicator() : const Text('Đăng nhập'),
              ),
              const SizedBox(height: 12), // Tạo khoảng cách nhỏ với nút Đăng nhập
              TextButton(
                onPressed: () {
                  // Chuyển sang màn hình Đăng ký
                  context.push('/register'); 
                },
                child: const Text('Chưa có tài khoản? Đăng ký ngay'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _onSubmit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _loading = true);
    
    try {
      final success = await ref.read(authProvider.notifier).login(
        _usernameCtrl.text.trim(), 
        _passwordCtrl.text.trim()
      );
      
      setState(() => _loading = false);
      
      if (!success) {
        // Nếu authProvider trả về false nhưng không quăng lỗi, báo thất bại chung chung
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đăng nhập thất bại. Vui lòng kiểm tra lại.'))
        );
      }
    } on DioException catch (e) {
      setState(() => _loading = false);
      // ĐÂY LÀ PHẦN QUAN TRỌNG NHẤT: Bắt chính xác thông báo lỗi từ Spring Boot
      String errorMsg = 'Lỗi mạng hoặc Server không phản hồi';
      
      if (e.response != null) {
        print('====== LỖI BACKEND TRẢ VỀ ======');
        print('Status Code: ${e.response?.statusCode}');
        print('Dữ liệu lỗi: ${e.response?.data}');
        print('=================================');
        
        // Cố gắng lấy thông báo lỗi chi tiết nếu Backend có gửi kèm
        if (e.response?.data is Map && e.response?.data['message'] != null) {
           errorMsg = e.response?.data['message'];
        } else {
           errorMsg = 'Lỗi ${e.response?.statusCode}: Đăng nhập thất bại';
        }
      }
      
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(errorMsg), backgroundColor: Colors.red)
      );
    } catch (e) {
      setState(() => _loading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Lỗi không xác định: $e'))
      );
    }
  }
}