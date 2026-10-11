import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import 'package:ecommerce_app/features/cart/presentation/pages/cart_screen.dart';
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
    CartScreen(),
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
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (value) => setState(() {
          _currentIndex = value;
          _visited.add(value);
        }),
        destinations: [
          const NavigationDestination(
            icon: Icon(Icons.storefront_outlined),
            selectedIcon: Icon(Icons.storefront),
            label: 'Sản phẩm',
          ),
          if (customer) ...[
            const NavigationDestination(
              icon: Icon(Icons.shopping_bag_outlined),
              label: 'Giỏ hàng',
            ),
            const NavigationDestination(
              icon: Icon(Icons.receipt_long_outlined),
              label: 'Đơn hàng',
            ),
            const NavigationDestination(
              icon: Icon(Icons.shield_outlined),
              label: 'Bảo hành',
            ),
            const NavigationDestination(
              icon: Icon(Icons.support_agent_outlined),
              label: 'Tickets',
            ),
          ],
          const NavigationDestination(
            icon: Icon(Icons.person_outline),
            label: 'Tài khoản',
          ),
        ],
      ),
    );
  }
}
