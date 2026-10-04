import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/presentation/providers/auth_provider.dart';
import '../../features/auth/presentation/pages/login_screen.dart';
import '../../features/auth/presentation/pages/register_screen.dart';
import '../../features/main/presentation/pages/main_main_screen.dart';

GoRouter createRouter(Ref ref) {
  return GoRouter(
    initialLocation: '/login',
    refreshListenable: ref.read(authProvider.notifier).authListenable,
    routes: [
      GoRoute(path: '/login', builder: (ctx, state) => const LoginScreen()),
      GoRoute(
        path: '/register',
        builder: (ctx, state) => const RegisterScreen(),
      ),
      GoRoute(path: '/main', builder: (ctx, state) => const MainMainScreen()),
    ],
    redirect: (context, state) {
      final auth = ref.read(authProvider);
      final loggingIn = state.matchedLocation == '/login';
      final registering = state.matchedLocation == '/register';

      if (auth.status == AuthStateStatus.initializing) return null;

      if (auth.status == AuthStateStatus.unauthenticated) {
        if (loggingIn || registering) return null;
        return '/login';
      }

      if (auth.status == AuthStateStatus.authenticated) {
        if (loggingIn || registering) return '/main';
      }

      return null;
    },
  );
}

final appRouterProvider = Provider<GoRouter>((ref) {
  return createRouter(ref);
});
