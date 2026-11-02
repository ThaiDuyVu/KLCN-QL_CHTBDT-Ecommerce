import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/address_model.dart';
import '../../data/address_repository.dart';
import '../../data/payment_method.dart';

final addressRepositoryProvider = Provider<AddressRepository>((ref) {
  return AddressRepository();
});

final addressListProvider = FutureProvider.autoDispose<List<AddressModel>>((ref) async {
  try {
    final repo = ref.watch(addressRepositoryProvider);
    return await repo.fetchAddresses();
  } catch (_) {
    return <AddressModel>[];
  }
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
