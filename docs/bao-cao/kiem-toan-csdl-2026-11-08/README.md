# Evidence kiểm toán CSDL — 08/11/2026

Đọc [báo cáo](../KIEM_TOAN_CSDL_THEO_DE_CUONG_2026-11-08.md) trước khi diễn giải kết quả. Các probe chẩn đoán cố ý xác nhận hành vi sai; PASS của probe không phải PASS nghiệm thu nghiệp vụ.

- `results-sanitized.json`: kết quả, lệnh, thời gian, exit code và phát hiện D01–D16.
- `metadata-sanitized.json`: metadata và số lượng tổng hợp của DB local.
- `source-sha256.json`: hash migration/nguồn trọng tâm của working tree tại lúc audit.
- `live-readonly-checks.sql`, `.txt`: truy vấn chỉ đọc và kết quả tổng hợp, không chứa định danh khách hay nội dung hội thoại.
- `DatabaseDesignAuditTest.java`: source fixture kiểm toán, đặt ngoài source set để không trở thành test chấp nhận các lỗi.

## Chạy lại

Cần Docker và môi trường JDK của repository. Test kế thừa `ReturnIntegrationTest` với PostgreSQL Testcontainers riêng. Fixture có TRUNCATE trên DB test của container; **không chuyển datasource của test sang DB nghiệp vụ**. Không chạy SQL sửa fixture thủ công trên DB local.

1. Sao chép `DatabaseDesignAuditTest.java` vào `backend/src/test/java/com/example/backend/returns/` nếu chưa có file cùng tên.
2. Từ root, nạp môi trường và chạy:

```sh
source scripts/env.sh
cd backend
./mvnw -B -Dapi.version=1.44 '-Dtest=DatabaseDesignAuditTest#D*' test
```

Trên Docker Desktop macOS của máy audit, tiến trình test dùng `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock` và `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`. Máy khác dùng Docker endpoint đúng với môi trường của mình.

3. Sau khi chạy, gỡ đúng bản sao tạm khỏi source test nếu không còn cần. Giữ source evidence tại đây.
4. Chạy các nhóm sau bằng **lần Maven riêng**, không gộp superclass `ReturnIntegrationTest` và subclass chẩn đoán trong cùng lần chạy vì lifecycle container kế thừa/context cache:

```sh
./mvnw -B -Dapi.version=1.44 -Dtest=ReturnIntegrationTest test
./mvnw -B -Dapi.version=1.44 '-Dtest=SchemaConstraintsIntegrationTest,ReportCategoryIntegrationTest' test
```

Khi sửa business behavior về sau, phải chuyển các probe tương ứng thành regression với kỳ vọng đúng, thay vì giữ assertion chấp nhận lỗi rồi báo đã sửa. D02 chỉ chứng minh giới hạn component bảo hành, chưa phải luồng bán lại đã chạy đến cấp bảo hành.

Raw log các lần chạy nằm ở `.local/db-audit-20261108/` trên máy audit; không commit raw output hạ tầng. Lần chạy gộp exit 1 vẫn được ghi trong JSON, tách khỏi các lần chạy hoàn tất.
