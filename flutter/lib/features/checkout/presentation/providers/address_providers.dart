import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/address_model.dart';
import '../../data/address_repository.dart';
import '../../data/payment_config_repository.dart';
import '../../data/payment_method.dart';

final addressRepositoryProvider = Provider<AddressRepository>((ref) {
  return AddressRepository();
});

// Không nuốt lỗi: màn hình cần phân biệt "chưa có địa chỉ" với "tải địa chỉ thất bại".
final addressListProvider = FutureProvider.autoDispose<List<AddressModel>>((ref) {
  return ref.watch(addressRepositoryProvider).fetchAddresses();
});

class SelectedAddressNotifier extends StateNotifier<AddressModel?> {
  SelectedAddressNotifier() : super(null);

  void select(AddressModel? address) {
    state = address;
  }
}

final selectedAddressProvider =
    StateNotifierProvider<SelectedAddressNotifier, AddressModel?>((ref) {
  return SelectedAddressNotifier();
});

final selectedPaymentMethodProvider = StateProvider<PaymentMethodOption>((ref) {
  return PaymentMethodOption.cod;
});

final paymentConfigRepositoryProvider = Provider<PaymentConfigRepository>((ref) {
  return PaymentConfigRepository();
});

final vnpayEnabledProvider = FutureProvider.autoDispose<bool>((ref) {
  return ref.watch(paymentConfigRepositoryProvider).isVnpayEnabled();
});

final installmentProvidersProvider =
    FutureProvider.autoDispose<List<InstallmentProvider>>((ref) {
  return ref.watch(paymentConfigRepositoryProvider).fetchActiveInstallmentProviders();
});
