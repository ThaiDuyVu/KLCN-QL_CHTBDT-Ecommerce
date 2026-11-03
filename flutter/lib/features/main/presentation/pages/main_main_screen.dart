import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import 'package:ecommerce_app/features/orders/presentation/pages/order_list_screen.dart';
import 'package:ecommerce_app/features/products/presentation/pages/home_screen.dart';
import 'package:ecommerce_app/features/profile/presentation/pages/profile_screen.dart';
import 'package:ecommerce_app/features/warranty/presentation/pages/warranty_list_screen.dart';
import 'package:ecommerce_app/features/warranty/presentation/pages/warranty_tickets_screen.dart';

class MainMainScreen extends ConsumerStatefulWidget {
  const MainMainScreen({super.key});

  @override
  ConsumerState<MainMainScreen> createState() => _MainMainScreenState();
}

class _MainMainScreenState extends ConsumerState<MainMainScreen> {
  int _currentIndex = 0;
  final Set<int> _visited = {0};

  static const List<Widget> _pages = [
    HomeScreen(),
    OrderListScreen(),
    WarrantyListScreen(),
    WarrantyTicketsScreen(),
    ProfileScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    final customer = ref.watch(authProvider).user?['roleName'] == 'CUSTOMER';
    final pages = customer
        ? _pages
        : const <Widget>[HomeScreen(), ProfileScreen()];
    final index = _currentIndex < pages.length ? _currentIndex : 0;

    return Scaffold(
      body: IndexedStack(
        index: index,
        children: [
          for (var i = 0; i < pages.length; i++)
            if (_visited.contains(i)) pages[i] else const SizedBox.shrink(),
        ],
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: index,
        type: BottomNavigationBarType.fixed,
        backgroundColor: Colors.white,
        elevation: 8,
        iconSize: 22,
        selectedFontSize: 10,
        unselectedFontSize: 10,
        selectedItemColor: Colors.deepOrange,
        unselectedItemColor: Colors.grey.shade600,
        onTap: (value) => setState(() {
          _currentIndex = value;
          _visited.add(value);
        }),
        items: [
          const BottomNavigationBarItem(
            icon: Icon(Icons.home_outlined),
            activeIcon: Icon(Icons.home_filled),
            label: 'Home',
          ),
          if (customer) ...[
            const BottomNavigationBarItem(
              icon: Icon(Icons.receipt_long_outlined),
              activeIcon: Icon(Icons.receipt_long),
              label: 'Đơn hàng',
            ),
            const BottomNavigationBarItem(
              icon: Icon(Icons.security_outlined),
              activeIcon: Icon(Icons.security),
              label: 'Bảo hành',
            ),
            const BottomNavigationBarItem(
              icon: Icon(Icons.confirmation_number_outlined),
              activeIcon: Icon(Icons.confirmation_number),
              label: 'Ticket',
            ),
          ],
          const BottomNavigationBarItem(
            icon: Icon(Icons.person_outline),
            activeIcon: Icon(Icons.person),
            label: 'Tôi',
          ),
        ],
      ),
    );
  }
}
