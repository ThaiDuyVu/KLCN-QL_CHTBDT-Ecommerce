import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/foundation.dart';

import '../../data/auth_repository.dart';

enum AuthStateStatus { initializing, authenticated, unauthenticated }

class AuthState {
  final AuthStateStatus status;
  final Map<String, dynamic>? user;

  AuthState._(this.status, this.user);

  factory AuthState.initializing() => AuthState._(AuthStateStatus.initializing, null);
  factory AuthState.authenticated(Map<String, dynamic> user) => AuthState._(AuthStateStatus.authenticated, user);
  factory AuthState.unauthenticated() => AuthState._(AuthStateStatus.unauthenticated, null);
}

class AuthListenable extends ChangeNotifier {
  void trigger() => notifyListeners();
}

class AuthNotifier extends StateNotifier<AuthState> {
  final AuthRepository _repo;
  final AuthListenable authListenable = AuthListenable();

  AuthNotifier(this._repo) : super(AuthState.initializing()) {
    _init();
  }

  Future<void> _init() async {
    try {
      final resp = await _repo.getMe();
      if (resp.statusCode == 200) {
        state = AuthState.authenticated(resp.data);
        authListenable.trigger();
      } else {
        state = AuthState.unauthenticated();
        authListenable.trigger();
      }
    } catch (e) {
      state = AuthState.unauthenticated();
      authListenable.trigger();
    }
  }

  Future<bool> login(String username, String password) async {
    print('👉 1. [Provider] Bắt đầu gọi login');
    try {
      final resp = await _repo.login(username, password);
      print('👉 2. [Provider] Nhận phản hồi từ Server, statusCode: ${resp.statusCode}');
      
      if (resp.statusCode == 200) {
        state = AuthState.authenticated(resp.data);
        authListenable.trigger();
        return true;
      }
    } catch (e, stacktrace) {
      print('❌❌❌ [Provider] LỖI BỊ BẮT: $e');
      print(stacktrace);
      
      state = AuthState.unauthenticated();
      authListenable.trigger();
      
      throw e; 
    }
    
    state = AuthState.unauthenticated();
    authListenable.trigger();
    return false;
  }

  Future<void> logout() async {
    try {
      await _repo.logout();
    } catch (_) {}
    state = AuthState.unauthenticated();
    authListenable.trigger();
  }
}

final authRepositoryProvider = Provider((ref) => AuthRepository());

final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  final repo = ref.read(authRepositoryProvider);
  return AuthNotifier(repo);
});