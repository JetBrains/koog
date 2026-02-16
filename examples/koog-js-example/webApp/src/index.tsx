import React from 'react';
import ReactDOM from 'react-dom/client';
import { Greeting } from './components/Greeting/Greeting.tsx';

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('Failed to find the root element');

ReactDOM.createRoot(rootElement).render(
  <React.StrictMode>
    <Greeting />
  </React.StrictMode>
);