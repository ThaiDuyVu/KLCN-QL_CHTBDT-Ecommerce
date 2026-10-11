import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/network/api_error.dart';
import '../providers/address_providers.dart';

class AddAddressScreen extends ConsumerStatefulWidget {
  const AddAddressScreen({super.key});

  @override
  ConsumerState<AddAddressScreen> createState() => _AddAddressScreenState();
}

class _AddAddressScreenState extends ConsumerState<AddAddressScreen> {
  final _formKey = GlobalKey<FormState>();
  final _recipientName = TextEditingController();
  final _recipientPhone = TextEditingController();
  final _addressLine = TextEditingController();
  final _ward = TextEditingController();
  final _district = TextEditingController();
  final _province = TextEditingController();
  String _label = 'Nhà riêng';
  bool _isDefault = false;
  bool _saving = false;

  @override
  void dispose() {
    _recipientName.dispose();
    _recipientPhone.dispose();
    _addressLine.dispose();
    _ward.dispose();
    _district.dispose();
    _province.dispose();
    super.dispose();
  }

  Future<void> _saveAddress() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _saving = true);
    try {
      final repo = ref.read(addressRepositoryProvider);
      final payload = {
        'label': _label,
        'recipientName': _recipientName.text.trim(),
        'recipientPhone': _recipientPhone.text.trim(),
        'addressLine': _addressLine.text.trim(),
        'ward': _ward.text.trim(),
        'district': _district.text.trim(),
        'province': _province.text.trim(),
      };

      final created = await repo.createAddress(payload);
      if (_isDefault) {
        try {
          await repo.makeDefault(created.id);
        } catch (_) {}
      }

      ref.invalidate(addressListProvider);
      ref.read(selectedAddressProvider.notifier).select(created);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Thêm địa chỉ giao hàng thành công!')),
        );
        Navigator.pop(context, created);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(apiErrorMessage(e))),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    const orangeColor = Color(0xffea580c);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Thêm địa chỉ mới'),
        elevation: 0,
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            const Text(
              'Liên hệ',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _recipientName,
              decoration: const InputDecoration(
                labelText: 'Họ và tên người nhận *',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.person_outline),
              ),
              validator: (v) => (v == null || v.trim().isEmpty)
                  ? 'Vui lòng nhập tên người nhận'
                  : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _recipientPhone,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(
                labelText: 'Số điện thoại *',
                hintText: '0901234567 hoặc +84901234567',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.phone_outlined),
              ),
              validator: (v) {
                final s = v?.trim() ?? '';
                if (s.isEmpty) return 'Vui lòng nhập số điện thoại';
                final ok = RegExp(r'^(?:0[0-9]{9}|\+84[0-9]{9})$').hasMatch(s);
                return ok ? null : 'Số điện thoại không hợp lệ (10 chữ số)';
              },
            ),
            const SizedBox(height: 20),
            const Text(
              'Địa chỉ nhận hàng',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _addressLine,
              decoration: const InputDecoration(
                labelText: 'Địa chỉ cụ thể (Số nhà, tên đường) *',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.home_outlined),
              ),
              validator: (v) => (v == null || v.trim().isEmpty)
                  ? 'Vui lòng nhập số nhà, tên đường'
                  : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _ward,
              decoration: const InputDecoration(
                labelText: 'Phường / Xã',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.location_city_outlined),
              ),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _district,
              decoration: const InputDecoration(
                labelText: 'Quận / Huyện',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.map_outlined),
              ),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _province,
              decoration: const InputDecoration(
                labelText: 'Tỉnh / Thành phố *',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.location_on_outlined),
              ),
              validator: (v) => (v == null || v.trim().isEmpty)
                  ? 'Vui lòng nhập Tỉnh / Thành phố'
                  : null,
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                const Text('Loại địa chỉ: ', style: TextStyle(fontWeight: FontWeight.w600)),
                ChoiceChip(
                  label: const Text('Nhà riêng'),
                  selected: _label == 'Nhà riêng',
                  onSelected: (selected) {
                    if (selected) setState(() => _label = 'Nhà riêng');
                  },
                ),
                const SizedBox(width: 8),
                ChoiceChip(
                  label: const Text('Văn phòng'),
                  selected: _label == 'Văn phòng',
                  onSelected: (selected) {
                    if (selected) setState(() => _label = 'Văn phòng');
                  },
                ),
              ],
            ),
            const SizedBox(height: 12),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Đặt làm địa chỉ mặc định'),
              activeThumbColor: orangeColor,
              value: _isDefault,
              onChanged: (val) => setState(() => _isDefault = val),
            ),
            const SizedBox(height: 24),
            SizedBox(
              height: 48,
              child: FilledButton(
                style: FilledButton.styleFrom(
                  backgroundColor: orangeColor,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                ),
                onPressed: _saving ? null : _saveAddress,
                child: _saving
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                      )
                    : const Text(
                        'Hoàn thành',
                        style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
