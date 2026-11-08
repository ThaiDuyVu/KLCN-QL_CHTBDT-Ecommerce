import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { AuthProvider } from './auth/AuthContext';
import CartProvider from './features/cart/CartProvider';
import WarehouseProvider from './features/warehouses/WarehouseProvider';
import App from './App';
import './styles/index.css';
import './styles/storefront.css';

// A tab opened before a redeploy still references old hashed chunks that no longer exist.
// Reload once to pick up the new build instead of leaving a blank page; the guard stops loops.
window.addEventListener('vite:preloadError', (event) => {
  const key = 'chunk-reload-at';
  let last = 0;
  try { last = Number(sessionStorage.getItem(key)) || 0; } catch { /* storage may be disabled */ }
  if (Date.now() - last < 10000) return;
  event.preventDefault();
  try { sessionStorage.setItem(key, String(Date.now())); } catch { /* storage may be disabled */ }
  window.location.reload();
});

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider><CartProvider><WarehouseProvider><App /></WarehouseProvider></CartProvider></AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
