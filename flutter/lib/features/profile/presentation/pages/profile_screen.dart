import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/network/api_error.dart';
import '../../../../core/utils/custom_snackbar.dart';
import '../../../../core/widgets/offline_error_view.dart';
import '../../../../core/widgets/shimmer_loading.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../../checkout/presentation/pages/add_address_screen.dart';
import '../../../checkout/presentation/providers/address_providers.dart';
import '../providers/profile_provider.dart';

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  String _formatRole(String? role) {
    switch (role?.toUpperCase()) {
      case 'CUSTOMER':
      case 'ROLE_CUSTOMER':
        return 'Khách hàng thành viên';
      case 'ADMIN':
      case 'ROLE_ADMIN':
        return 'Quản trị viên (Admin)';
      case 'MANAGER':
      case 'ROLE_MANAGER':
        return 'Quản lý chi nhánh (Manager)';
      case 'STAFF':
      case 'ROLE_STAFF':
        return 'Nhân viên bán hàng (Staff)';
      default:
        return role ?? 'Khách hàng';
    }
  }

  String _getInitials(String name) {
    if (name.trim().isEmpty) return 'U';
    final parts = name.trim().split(' ');
    if (parts.length >= 2) {
      return '${parts.first[0]}${parts.last[0]}'.toUpperCase();
    }
    return name.substring(0, 1).toUpperCase();
  }

  Future<void> _handleLogout(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
        ),
        title: const Row(
          children: [
            Icon(Icons.logout_rounded, color: Colors.deepOrange),
            SizedBox(width: 10),
            Text('Đăng xuất'),
          ],
        ),
        content: const Text(
          'Bạn có chắc chắn muốn đăng xuất khỏi tài khoản không?',
          style: TextStyle(fontSize: 14),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Hủy'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.deepOrange,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
            child: const Text('Đăng xuất'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    try {
      await ref.read(authProvider.notifier).logout();
      if (context.mounted) {
        CustomSnackbar.showSuccess(context, 'Đã đăng xuất tài khoản thành công');
        context.go('/login');
      }
    } catch (_) {
      if (context.mounted) {
        CustomSnackbar.showError(context, 'Đăng xuất thất bại. Vui lòng thử lại.');
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profileAsync = ref.watch(profileProvider);
    final authUser = ref.watch(authProvider).user ?? {};
    final addressListAsync = ref.watch(addressListProvider);

    return Scaffold(
      backgroundColor: const Color(0xFFF7F8FA),
      body: profileAsync.when(
        data: (user) {
          // Kết hợp dữ liệu từ profileProvider và authProvider
          final mergedUser = {...authUser, ...user};
          final displayName = (mergedUser['displayName'] ??
                  mergedUser['fullName'] ??
                  mergedUser['name'] ??
                  mergedUser['username'] ??
                  'Người dùng')
              .toString();
          final username = (mergedUser['username'] ?? '-').toString();
          final roleName = (mergedUser['roleName'] ?? 'CUSTOMER').toString();
          final email = (mergedUser['email'] ?? '').toString();
          final userId = (mergedUser['userId'] ?? mergedUser['id'] ?? '').toString();
          final rawAddress = (mergedUser['address'] ?? mergedUser['shippingAddress'] ?? mergedUser['addressLine'] ?? mergedUser['fullAddress'] ?? '').toString().trim();

          return RefreshIndicator(
            onRefresh: () async {
              ref.invalidate(profileProvider);
              ref.invalidate(addressListProvider);
              await Future.wait([
                ref.read(profileProvider.future),
                ref.read(addressListProvider.future),
              ]);
            },
            child: SingleChildScrollView(
              physics: const AlwaysScrollableScrollPhysics(),
              child: Column(
                children: [
                  // 1. Header Gradient Shopee Style
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.fromLTRB(20, 60, 20, 32),
                    decoration: const BoxDecoration(
                      gradient: LinearGradient(
                        colors: [
                          Color(0xFFFF5722),
                          Color(0xFFE64A19),
                        ],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      borderRadius: BorderRadius.vertical(
                        bottom: Radius.circular(28),
                      ),
                    ),
                    child: Column(
                      children: [
                        // Avatar tròn có viền trắng nổi bật và bóng đổ
                        Container(
                          width: 86,
                          height: 86,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            border: Border.all(
                              color: Colors.white,
                              width: 3.5,
                            ),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withValues(alpha: 0.15),
                                blurRadius: 10,
                                offset: const Offset(0, 4),
                              ),
                            ],
                            color: Colors.deepOrange.shade100,
                          ),
                          child: Center(
                            child: Text(
                              _getInitials(displayName),
                              style: const TextStyle(
                                fontSize: 32,
                                fontWeight: FontWeight.bold,
                                color: Colors.deepOrange,
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(height: 14),
                        // Tên người dùng
                        Text(
                          displayName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        ),
                        const SizedBox(height: 8),
                        // Chip Vai trò (Role Badge)
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 14,
                            vertical: 5,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: 0.22),
                            borderRadius: BorderRadius.circular(20),
                            border: Border.all(
                              color: Colors.white.withValues(alpha: 0.35),
                              width: 1,
                            ),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(
                                Icons.verified_user_rounded,
                                size: 14,
                                color: Colors.white,
                              ),
                              const SizedBox(width: 6),
                              Text(
                                _formatRole(roleName),
                                style: const TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                  color: Colors.white,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 18),

                  // 2. Card Thông tin tài khoản thực tế
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Material(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                      elevation: 1,
                      shadowColor: Colors.black12,
                      clipBehavior: Clip.antiAlias,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Padding(
                            padding: EdgeInsets.fromLTRB(16, 16, 16, 8),
                            child: Row(
                              children: [
                                Icon(
                                  Icons.account_box_outlined,
                                  size: 20,
                                  color: Colors.deepOrange,
                                ),
                                SizedBox(width: 8),
                                Text(
                                  'Thông tin tài khoản',
                                  style: TextStyle(
                                    fontSize: 15,
                                    fontWeight: FontWeight.bold,
                                    color: Colors.black87,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const Divider(height: 1),
                          _buildInfoTile(
                            icon: Icons.alternate_email_rounded,
                            title: 'Tên đăng nhập',
                            value: '@$username',
                          ),
                          if (userId.isNotEmpty)
                            _buildInfoTile(
                              icon: Icons.badge_outlined,
                              title: 'Mã người dùng',
                              value: userId,
                            ),
                          _buildInfoTile(
                            icon: Icons.security_rounded,
                            title: 'Quyền truy cập',
                            value: roleName,
                          ),
                          if (email.isNotEmpty)
                            _buildInfoTile(
                              icon: Icons.email_outlined,
                              title: 'Email',
                              value: email,
                            ),
                          if (rawAddress.isNotEmpty)
                            _buildInfoTile(
                              icon: Icons.home_outlined,
                              title: 'Địa chỉ hồ sơ',
                              value: rawAddress,
                            ),
                        ],
                      ),
                    ),
                  ),

                  const SizedBox(height: 16),

                  // 3. Card Sổ địa chỉ nhận hàng (Multi-address & Thêm địa chỉ mới)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Material(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                      elevation: 1,
                      shadowColor: Colors.black12,
                      clipBehavior: Clip.antiAlias,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Padding(
                            padding: const EdgeInsets.fromLTRB(16, 10, 8, 8),
                            child: Row(
                              children: [
                                const Icon(
                                  Icons.location_on_rounded,
                                  size: 20,
                                  color: Colors.deepOrange,
                                ),
                                const SizedBox(width: 8),
                                const Text(
                                  'Địa chỉ nhận hàng',
                                  style: TextStyle(
                                    fontSize: 15,
                                    fontWeight: FontWeight.bold,
                                    color: Colors.black87,
                                  ),
                                ),
                                const Spacer(),
                                TextButton.icon(
                                  onPressed: () async {
                                    await Navigator.push(
                                      context,
                                      MaterialPageRoute(
                                        builder: (_) => const AddAddressScreen(),
                                      ),
                                    );
                                    ref.invalidate(addressListProvider);
                                  },
                                  icon: const Icon(
                                    Icons.add_rounded,
                                    size: 18,
                                    color: Colors.deepOrange,
                                  ),
                                  label: const Text(
                                    'Thêm mới',
                                    style: TextStyle(
                                      fontSize: 13,
                                      fontWeight: FontWeight.bold,
                                      color: Colors.deepOrange,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const Divider(height: 1),
                          addressListAsync.when(
                            loading: () => const Padding(
                              padding: EdgeInsets.all(24),
                              child: Center(
                                child: SizedBox(
                                  width: 24,
                                  height: 24,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.deepOrange,
                                  ),
                                ),
                              ),
                            ),
                            error: (error, _) => Padding(
                              padding: const EdgeInsets.all(16),
                              child: Row(
                                children: [
                                  const Icon(
                                    Icons.error_outline_rounded,
                                    color: Colors.red,
                                    size: 20,
                                  ),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Text(
                                      apiErrorMessage(error),
                                      style: const TextStyle(
                                        fontSize: 13,
                                        color: Colors.black87,
                                      ),
                                    ),
                                  ),
                                  TextButton(
                                    onPressed: () => ref.invalidate(addressListProvider),
                                    child: const Text('Thử lại'),
                                  ),
                                ],
                              ),
                            ),
                            data: (addresses) {
                              if (addresses.isEmpty) {
                                return Padding(
                                  padding: const EdgeInsets.symmetric(
                                    vertical: 24,
                                    horizontal: 16,
                                  ),
                                  child: Center(
                                    child: Column(
                                      children: [
                                        Icon(
                                          Icons.location_off_outlined,
                                          size: 40,
                                          color: Colors.grey.shade400,
                                        ),
                                        const SizedBox(height: 8),
                                        Text(
                                          'Bạn chưa lưu địa chỉ nhận hàng nào',
                                          style: TextStyle(
                                            fontSize: 14,
                                            color: Colors.grey.shade600,
                                          ),
                                        ),
                                        const SizedBox(height: 12),
                                        OutlinedButton.icon(
                                          style: OutlinedButton.styleFrom(
                                            foregroundColor: Colors.deepOrange,
                                            side: const BorderSide(
                                              color: Colors.deepOrange,
                                            ),
                                            shape: RoundedRectangleBorder(
                                              borderRadius:
                                                  BorderRadius.circular(8),
                                            ),
                                          ),
                                          onPressed: () async {
                                            await Navigator.push(
                                              context,
                                              MaterialPageRoute(
                                                builder: (_) =>
                                                    const AddAddressScreen(),
                                              ),
                                            );
                                            ref.invalidate(addressListProvider);
                                          },
                                          icon: const Icon(
                                            Icons.add_rounded,
                                            size: 18,
                                          ),
                                          label: const Text('Thêm địa chỉ ngay'),
                                        ),
                                      ],
                                    ),
                                  ),
                                );
                              }

                              return ListView.separated(
                                shrinkWrap: true,
                                physics: const NeverScrollableScrollPhysics(),
                                itemCount: addresses.length,
                                separatorBuilder: (_, _) => const Divider(
                                  height: 1,
                                  indent: 16,
                                  endIndent: 16,
                                ),
                                itemBuilder: (context, index) {
                                  final addr = addresses[index];
                                  return Padding(
                                    padding: const EdgeInsets.all(16),
                                    child: Row(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        Container(
                                          padding: const EdgeInsets.all(8),
                                          decoration: BoxDecoration(
                                            color: addr.isDefault
                                                ? Colors.deepOrange.shade50
                                                : Colors.grey.shade100,
                                            borderRadius:
                                                BorderRadius.circular(8),
                                          ),
                                          child: Icon(
                                            addr.label == 'Văn phòng'
                                                ? Icons.business_outlined
                                                : Icons.home_outlined,
                                            size: 20,
                                            color: addr.isDefault
                                                ? Colors.deepOrange
                                                : Colors.grey.shade700,
                                          ),
                                        ),
                                        const SizedBox(width: 12),
                                        Expanded(
                                          child: Column(
                                            crossAxisAlignment:
                                                CrossAxisAlignment.start,
                                            children: [
                                              Row(
                                                children: [
                                                  Text(
                                                    addr.recipientName,
                                                    style: const TextStyle(
                                                      fontSize: 14,
                                                      fontWeight:
                                                          FontWeight.bold,
                                                      color: Colors.black87,
                                                    ),
                                                  ),
                                                  Text(
                                                    '  |  ${addr.phoneNumber}',
                                                    style: TextStyle(
                                                      fontSize: 13,
                                                      color: Colors
                                                          .grey.shade700,
                                                    ),
                                                  ),
                                                ],
                                              ),
                                              const SizedBox(height: 4),
                                              Text(
                                                addr.formattedAddress,
                                                style: const TextStyle(
                                                  fontSize: 13,
                                                  color: Colors.black87,
                                                  height: 1.3,
                                                ),
                                              ),
                                              const SizedBox(height: 6),
                                              Wrap(
                                                spacing: 6,
                                                runSpacing: 4,
                                                crossAxisAlignment:
                                                    WrapCrossAlignment.center,
                                                children: [
                                                  if (addr.isDefault)
                                                    Container(
                                                      padding:
                                                          const EdgeInsets
                                                              .symmetric(
                                                        horizontal: 6,
                                                        vertical: 2,
                                                      ),
                                                      decoration: BoxDecoration(
                                                        color: Colors
                                                            .deepOrange.shade50,
                                                        border: Border.all(
                                                          color: Colors
                                                              .deepOrange,
                                                          width: 0.8,
                                                        ),
                                                        borderRadius:
                                                            BorderRadius
                                                                .circular(4),
                                                      ),
                                                      child: const Text(
                                                        'Mặc định',
                                                        style: TextStyle(
                                                          fontSize: 11,
                                                          fontWeight:
                                                              FontWeight.w600,
                                                          color: Colors
                                                              .deepOrange,
                                                        ),
                                                      ),
                                                    ),
                                                  if (addr.label != null &&
                                                      addr.label!.isNotEmpty)
                                                    Container(
                                                      padding:
                                                          const EdgeInsets
                                                              .symmetric(
                                                        horizontal: 6,
                                                        vertical: 2,
                                                      ),
                                                      decoration: BoxDecoration(
                                                        color: Colors
                                                            .grey.shade100,
                                                        border: Border.all(
                                                          color: Colors
                                                              .grey.shade400,
                                                          width: 0.8,
                                                        ),
                                                        borderRadius:
                                                            BorderRadius
                                                                .circular(4),
                                                      ),
                                                      child: Text(
                                                        addr.label!,
                                                        style: TextStyle(
                                                          fontSize: 11,
                                                          color: Colors
                                                              .grey.shade800,
                                                        ),
                                                      ),
                                                    ),
                                                  if (!addr.isDefault)
                                                    InkWell(
                                                      onTap: () async {
                                                        try {
                                                          await ref
                                                              .read(
                                                                addressRepositoryProvider,
                                                              )
                                                              .makeDefault(
                                                                addr.id,
                                                              );
                                                          ref.invalidate(
                                                            addressListProvider,
                                                          );
                                                          if (context.mounted) {
                                                            CustomSnackbar
                                                                .showSuccess(
                                                              context,
                                                              'Đã đặt làm địa chỉ mặc định',
                                                            );
                                                          }
                                                        } catch (e) {
                                                          if (context.mounted) {
                                                            CustomSnackbar
                                                                .showError(
                                                              context,
                                                              'Không thể đặt mặc định: ${apiErrorMessage(e)}',
                                                            );
                                                          }
                                                        }
                                                      },
                                                      child: const Padding(
                                                        padding:
                                                            EdgeInsets
                                                                .symmetric(
                                                          vertical: 2,
                                                        ),
                                                        child: Text(
                                                          'Thiết lập mặc định',
                                                          style: TextStyle(
                                                            fontSize: 12,
                                                            fontWeight:
                                                                FontWeight.w500,
                                                            color: Colors
                                                                .deepOrange,
                                                          ),
                                                        ),
                                                      ),
                                                    ),
                                                ],
                                              ),
                                            ],
                                          ),
                                        ),
                                        IconButton(
                                          icon: Icon(
                                            Icons.delete_outline,
                                            size: 20,
                                            color: Colors.grey.shade500,
                                          ),
                                          tooltip: 'Xóa địa chỉ',
                                          onPressed: () async {
                                            final confirm =
                                                await showDialog<bool>(
                                              context: context,
                                              builder: (ctx) => AlertDialog(
                                                title: const Text(
                                                  'Xác nhận xóa',
                                                ),
                                                content: const Text(
                                                  'Bạn có chắc muốn xóa địa chỉ nhận hàng này không?',
                                                ),
                                                actions: [
                                                  TextButton(
                                                    onPressed: () =>
                                                        Navigator.pop(
                                                      ctx,
                                                      false,
                                                    ),
                                                    child: const Text('Hủy'),
                                                  ),
                                                  TextButton(
                                                    onPressed: () =>
                                                        Navigator.pop(
                                                      ctx,
                                                      true,
                                                    ),
                                                    child: const Text(
                                                      'Xóa',
                                                      style: TextStyle(
                                                        color: Colors.red,
                                                      ),
                                                    ),
                                                  ),
                                                ],
                                              ),
                                            );
                                            if (confirm == true) {
                                              try {
                                                await ref
                                                    .read(
                                                      addressRepositoryProvider,
                                                    )
                                                    .deleteAddress(addr.id);
                                                ref.invalidate(
                                                  addressListProvider,
                                                );
                                                if (context.mounted) {
                                                  CustomSnackbar.showSuccess(
                                                    context,
                                                    'Đã xóa địa chỉ thành công',
                                                  );
                                                }
                                              } catch (e) {
                                                if (context.mounted) {
                                                  CustomSnackbar.showError(
                                                    context,
                                                    'Không thể xóa: ${apiErrorMessage(e)}',
                                                  );
                                                }
                                              }
                                            }
                                          },
                                        ),
                                      ],
                                    ),
                                  );
                                },
                              );
                            },
                          ),
                        ],
                      ),
                    ),
                  ),

                  const SizedBox(height: 16),

                  // 4. Card Thao tác tài khoản (Chức năng thực tế hoạt động 100%)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Material(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                      elevation: 1,
                      shadowColor: Colors.black12,
                      clipBehavior: Clip.antiAlias,
                      child: Column(
                        children: [
                          ListTile(
                            leading: Container(
                              padding: const EdgeInsets.all(8),
                              decoration: BoxDecoration(
                                color: Colors.blue.withValues(alpha: 0.1),
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: const Icon(
                                Icons.refresh_rounded,
                                color: Colors.blue,
                                size: 20,
                              ),
                            ),
                            title: const Text(
                              'Làm mới dữ liệu hồ sơ',
                              style: TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            trailing: const Icon(
                              Icons.arrow_forward_ios_rounded,
                              size: 14,
                              color: Colors.grey,
                            ),
                            onTap: () {
                              ref.invalidate(profileProvider);
                              ref.invalidate(addressListProvider);
                              CustomSnackbar.showInfo(
                                context,
                                'Đang đồng bộ lại thông tin tài khoản & sổ địa chỉ...',
                              );
                            },
                          ),
                          const Divider(height: 1, indent: 56),
                          ListTile(
                            leading: Container(
                              padding: const EdgeInsets.all(8),
                              decoration: BoxDecoration(
                                color: Colors.red.withValues(alpha: 0.1),
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: const Icon(
                                Icons.logout_rounded,
                                color: Colors.red,
                                size: 20,
                              ),
                            ),
                            title: const Text(
                              'Đăng xuất',
                              style: TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w600,
                                color: Colors.red,
                              ),
                            ),
                            trailing: const Icon(
                              Icons.arrow_forward_ios_rounded,
                              size: 14,
                              color: Colors.grey,
                            ),
                            onTap: () => _handleLogout(context, ref),
                          ),
                        ],
                      ),
                    ),
                  ),

                  const SizedBox(height: 32),
                ],
              ),
            ),
          );
        },
        // Hiệu ứng Shimmer Loading chuẩn Shopee khi đang tải
        loading: () => const ProfileShimmer(),
        // Xử lý lỗi Offline hoặc lỗi kết nối mạng
        error: (error, _) => isNetworkError(error)
            ? OfflineErrorView(
                onRetry: () => ref.invalidate(profileProvider),
              )
            : Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(
                        Icons.error_outline_rounded,
                        size: 56,
                        color: Colors.redAccent,
                      ),
                      const SizedBox(height: 16),
                      const Text(
                        'Không thể tải thông tin hồ sơ',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        error.toString(),
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 12,
                          color: Colors.grey.shade600,
                        ),
                      ),
                      const SizedBox(height: 20),
                      ElevatedButton(
                        onPressed: () => ref.invalidate(profileProvider),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.deepOrange,
                          foregroundColor: Colors.white,
                        ),
                        child: const Text('Thử lại'),
                      ),
                    ],
                  ),
                ),
              ),
      ),
    );
  }

  Widget _buildInfoTile({
    required IconData icon,
    required String title,
    required String value,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        children: [
          Icon(icon, size: 20, color: Colors.grey.shade600),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: TextStyle(
                    fontSize: 11.5,
                    color: Colors.grey.shade600,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  value,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: Colors.black87,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
