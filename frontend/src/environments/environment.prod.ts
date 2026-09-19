export const environment = {
  production: true,
  // Khi chạy all-in-one trên cùng 1 container/domain: để '' (tương đối /api/...)
  // Khi deploy Frontend Static Site riêng trên Render: thay bằng URL backend Web Service của Render (VD: 'https://analyze-backend.onrender.com')
  apiUrl: ''
};
