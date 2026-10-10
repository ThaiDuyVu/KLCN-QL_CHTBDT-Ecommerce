# 1. Bơm key bí mật cho JWT (Cứ gõ bừa 1 chuỗi dài hơn 32 ký tự là được)
$env:JWT_SECRET="chuoi-khoa-bi-mat-cho-do-an-kltn-cua-tam-2026-dai-hon-32-ky-tu"

# 2. Bơm cấu hình Database từ Neon (Ghi đè luôn cấu hình localhost trong file yml)
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://ep-odd-poetry-b36qdrad-pooler.c-4.ap-southeast-1.aws.neon.tech/neondb?sslmode=require"
$env:SPRING_DATASOURCE_USERNAME="neondb_owner"
$env:SPRING_DATASOURCE_PASSWORD="npg_iwzn48cpLkAG"

# 3. Ra lệnh chạy ứng dụng bỏ qua test
.\mvnw spring-boot:run -DskipTests