# Sửa trực tiếp file Word gốc (khuyên dùng)

`sua_docx_goc.py` (dùng chung `common.py`) đọc 3 nguồn:
- file .docx tải từ Google Docs (File → Tải xuống → Microsoft Word);
- bản gốc txt `backend/docs/BaoCaoKhoaLuan.txt`;
- bản sao đã chỉnh `BaoCaoKhoaLuan_ChinhSua.txt`.

Sau đó chương trình áp dụng các thay đổi lên chính file .docx và **giữ định dạng, hình ảnh, style của bản gốc**:
- **Dòng đổi nội dung:** sửa ngay tại chỗ.
- **Đoạn mới:** chèn theo mẫu định dạng có sẵn trong file (Heading, gạch đầu dòng, chú thích, tài liệu tham khảo).
- **Phần đã bỏ** (UC-13, UC-SYS-13, 3.4.10, 3.5.12): xóa cả đoạn, bảng và hình.
- **Bảng mới:** dựng thành bảng Word; bảng từ viết tắt được bổ sung hàng.
- **Hình:** thay ảnh cũ bằng sơ đồ PlantUML ở 18 hình cần vẽ lại, thêm sơ đồ lớp phân tích.
- **Mục lục:** giữ trường TOC của Word, tự cập nhật khi mở file.

Kết quả: `BaoCaoKhoaLuan_ChinhSua_TuBanGoc.docx`.

```sh
cd <thư mục dự án>
PYTHONPATH=docs/bao-cao/cong-cu /tmp/docxenv/bin/python docs/bao-cao/cong-cu/sua_docx_goc.py \
  goc.docx backend/docs/BaoCaoKhoaLuan.txt docs/bao-cao/BaoCaoKhoaLuan_ChinhSua.txt /tmp/png \
  docs/bao-cao/BaoCaoKhoaLuan_ChinhSua_TuBanGoc.docx /tmp/log.json
```

# Tạo lại file Word từ bản sao txt (bản dựng mới, không có ảnh gốc)

`txt_sang_docx.py` chuyển `BaoCaoKhoaLuan_ChinhSua.txt` thành `BaoCaoKhoaLuan_ChinhSua.docx`. Kết quả gồm:

- **Trang và chữ:** A4; lề trên 2 cm, dưới 2 cm, trái 3 cm, phải 2 cm; Times New Roman 13, giãn dòng 1,5.
- **Tiêu đề:** dùng style Heading 1–4, mục lục tự động lấy từ đây.
- **Mục lục, danh mục hình, danh mục bảng:** là trường tự động. Khi mở file, Word hỏi có cập nhật trường không, chọn **Yes**.
- **Bảng:** chuyển thành bảng Word thật, gồm bảng đặc tả UC 2 cột, các bảng xuất từ Word và các bảng dạng tab.
- **Sơ đồ PlantUML:** chèn thành ảnh tại các dòng `[CẦN VẼ LẠI – ĐÃ PHÁC THẢO …]`.
- **Ảnh khác:** để ô giữ chỗ viền nét đứt, ghi số hình mới và số hình trong bản Word gốc.
- **Dòng đánh dấu `[CẦN …]` / `[GHI CHÚ …]`:** tô vàng.

## Chạy lại

```sh
python3 -m venv /tmp/docxenv && /tmp/docxenv/bin/pip install python-docx
mkdir -p /tmp/png
.local/tools/jdk-21.0.12.1+1/Contents/Home/bin/java -jar plantuml.jar -Playout=smetana -tpng -o /tmp/png docs/bao-cao/so-do/*.puml
cd docs/bao-cao
/tmp/docxenv/bin/python cong-cu/txt_sang_docx.py BaoCaoKhoaLuan_ChinhSua.txt cong-cu/doi_chieu_so_hinh.json /tmp/png BaoCaoKhoaLuan_ChinhSua.docx
```

Nếu máy có Graphviz, bỏ `-Playout=smetana` khi render để bố cục sơ đồ gọn hơn.

`doi_chieu_so_hinh.json` là bảng đối chiếu số hình mới và số hình trong bản gốc, sinh ra ở Giai đoạn 6.
