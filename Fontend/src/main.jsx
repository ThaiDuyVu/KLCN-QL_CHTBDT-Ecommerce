import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { AuthProvider } from './auth/AuthContext';
import CartProvider from './features/cart/CartProvider';
import App from './App';
import './styles/index.css';

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider><CartProvider><App /></CartProvider></AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
