import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ecommerce_app/features/cart/presentation/pages/cart_screen.dart';
import 'package:ecommerce_app/features/orders/presentation/pages/order_list_screen.dart';
import 'package:ecommerce_app/features/products/presentation/pages/home_screen.dart';
import 'package:ecommerce_app/features/profile/presentation/pages/profile_screen.dart';

class MainMainScreen extends ConsumerStatefulWidget {
  const MainMainScreen({super.key});

  @override
  ConsumerState<MainMainScreen> createState() => _MainMainScreenState();
}

class _MainMainScreenState extends ConsumerState<MainMainScreen> {
  int _currentIndex = 0;

  static const List<Widget> _pages = [
    HomeScreen(),
    CartScreen(),
    OrderListScreen(),
    ProfileScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: _pages,
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentIndex,
        onTap: (index) => setState(() => _currentIndex = index),
        type: BottomNavigationBarType.fixed,
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.home), label: 'Trang chủ'),
          BottomNavigationBarItem(icon: Icon(Icons.shopping_cart), label: 'Giỏ hàng'),
          BottomNavigationBarItem(icon: Icon(Icons.receipt_long), label: 'Đơn hàng'),
          BottomNavigationBarItem(icon: Icon(Icons.person), label: 'Cá nhân'),
        ],
      ),
    );
  }
}
