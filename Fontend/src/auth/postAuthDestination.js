// Where to go after login or registration: the protected page that sent the user here,
// limited to same-origin paths, otherwise the account page.
export function postAuthDestination(from) {
  const safePath = typeof from?.pathname === 'string' && from.pathname.startsWith('/')
    && !from.pathname.startsWith('//') && !from.pathname.includes('\\')
    && from.pathname !== '/login' && from.pathname !== '/register';
  return safePath ? `${from.pathname}${from.search || ''}${from.hash || ''}` : '/account';
}
