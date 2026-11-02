import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/repositories/warranty_repository.dart';
import '../../data/models/warranty_model.dart';

final warrantyRepositoryProvider = Provider((ref) => WarrantyRepository());

final myWarrantiesProvider = FutureProvider<List<WarrantyModel>>((ref) {
  return ref.read(warrantyRepositoryProvider).fetchMyWarranties();
});

final myWarrantyTicketsProvider = FutureProvider<List<WarrantyTicketModel>>((ref) {
  return ref.read(warrantyRepositoryProvider).fetchMyTickets();
});

final warrantyDetailProvider = FutureProvider.family<WarrantyModel, String>((ref, warrantyId) {
  return ref.read(warrantyRepositoryProvider).fetchWarrantyDetail(warrantyId);
});

final warrantyLookupProvider = FutureProvider.family<WarrantyModel, String>((ref, code) {
  return ref.read(warrantyRepositoryProvider).lookupMyWarranty(code);
});
