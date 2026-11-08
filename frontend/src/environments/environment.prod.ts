declare global {
  interface Window {
    analyzeConfig?: { apiUrl: string };
  }
}

export const environment = {
  production: true,
  apiUrl: typeof window !== 'undefined' ? (window.analyzeConfig?.apiUrl ?? '') : '',
};
