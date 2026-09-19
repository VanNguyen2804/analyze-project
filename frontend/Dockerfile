FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build --configuration=production

FROM nginx:alpine
# Lưu ý: Cần chỉnh 'analyzeproject' thành tên xuất ra trong angular.json của bạn
COPY --from=build /app/dist/analyzeproject /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
