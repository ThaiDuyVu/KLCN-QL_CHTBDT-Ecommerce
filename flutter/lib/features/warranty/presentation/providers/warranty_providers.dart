import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/repositories/warranty_repository.dart';
import '../../data/models/warranty_model.dart';

final warrantyRepositoryProvider = Provider((ref) => WarrantyRepository());

final myWarrantyProvider = FutureProvider<List<WarrantyTicket>>((ref) {
  return ref.read(warrantyRepositoryProvider).fetchMyWarranty();
});
