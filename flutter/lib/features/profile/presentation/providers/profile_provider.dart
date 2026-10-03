import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/repositories/profile_repository.dart';

final profileRepositoryProvider = Provider((ref) => ProfileRepository());

final profileProvider = FutureProvider<Map<String, dynamic>>((ref) {
  return ref.read(profileRepositoryProvider).getProfile();
});
