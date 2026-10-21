# UI readiness audit

**UI_LAB_READY: YES — development/flag-enabled Lab scope.** **UI_STOREFRONT_READY: NO / NOT_IMPLEMENTED.**19browser audit checks PASS; UI19PASS là xác minh thiếu storefront integration, không phải storefront featurePASS. Không tạo route/widget/menu mới.

Đã đọc `App.jsx`, `AppLayout.jsx`, `ChatbotLabPage.jsx`, `TracePanel.jsx`, `chatbotApi.js`, CSS và component tests. `/chatbot-lab` nằm dưới RequireAuth, ngoài storefront AppLayout, bật bằng DEV hoặc VITE_CHATBOT_LAB_ENABLED. Direct authenticated URL đã dùng; không tìm thấy customer-facing chat widget/menu ở storefront `/products`.

| Feature | Existing UI | Real browser tested | Expected | Actual | Status | Gap |
|---|---|---|---|---|---|---|
| Login / unauthorized | True | True | Anonymous /chatbot-lab redirects to login; synthetic CUSTOMER can log in with actual UI | {"anonymous_redirect": "/login", "login_form_real": true, "authenticated_route": "/chatbot-lab"} | PASS | — |
| Lab route / readiness | True | True | Chatbot Lab accessible directly; status shows real AI and PostgreSQL | {"ai_real": true, "storage": "postgres"} | PASS | — |
| ProductCards / facts | True | True | Actual product POST renders valid card/price/RAM/storage/SKU/stock and answer | {"card": {"product_id": "169710dd-d072-4e83-9fd0-e618d706ee26", "product_name": "Xiaomi 15", "variant_id": "aabb3778-d136-453e-8165-c8d2dcdce581", "sku": "DEV-X15-GRN-512", "original_price": "21990000.0", "effective_price": "21990000.0", "ram": "16GB", "storage": "512GB", "color": "Xanh lá", "available_quantity": 2, "warranty_months": 12, "image_url": "/images/products/xiaomi-15/main.jpg", "reasons": []}, "message": "Xiaomi 15\n- DEV-X15-GRN-512: RAM 16GB, bộ nhớ 512GB, màu Xanh lá; giá 21,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng."} | PASS | — |
| Product detail navigation | True | True | Product card navigates to real detail page | {"href": "/products/169710dd-d072-4e83-9fd0-e618d706ee26", "real_product_detail_loaded": true} | PASS | — |
| DEMO sources / missing official guide | True | True | DEMO Knowledge source shown as DEMO; absent official instructions explained | {"demo_sources": [{"document_id": "3da5065e-c979-5610-90d0-9e6e94ba0308", "chunk_id": "466d9bd6-e605-4688-83e7-0f0e825736e8", "title": "DEMO — Chính sách đổi trả kiểm thử", "source": "demo://chatbot-lab/return"}], "guidance_message": "Mình chưa có tài liệu chính thức đủ để hướng dẫn thao tác này. Mình không thể xác nhận quy trình hoặc chính sách của cửa hàng."} | PASS | — |
| New / switch / close sessions | True | True | Fresh session, owned session switch, closed session are usable | {"closed_disables_send": true, "fresh_works": true, "selected_old_owned_session": true} | PASS | — |
| Cursor history / reload | True | True | History reload and cursor pagination deduplicate persisted text; no invented cards | {"cursor_rows_after_older": 106, "unique_texts": 106, "reload_window": 100, "historical_cards": 0} | WORKING_WITH_LIMITATIONS | Text-only restored history. |
| Session pagination | True | True | Session list pagination advances exactly one page | {"option_count_before": 21, "after": 40, "requested_page": 1} | PASS | — |
| Send failure / safe retry | True | True | 503 before persistence displays error, preserves draft, safe retry has one displayed USER | {"injected_response": 503, "server_not_called_for_failure": true, "retry_real_post": true, "displayed_users": 1} | PASS | — |
| Switch during pending requests | True | True | Switching sessions while POST pending cannot overwrite selected history | {"switch_disabled_during_post": true, "switch_after_completion": true, "selected_session_kept": true, "note": "UI prevents switching during POST; component tests separately cover stale close responses."} | PASS | — |
| Double-submit / loading | True | True | Double-submit while pending sends one POST; loading state visible | {"post_count": 1, "loading_visible": true} | PASS | — |
| Feedback | True | True | Feedback controls call real endpoint and show selected state | {"http_status": 200, "selected": true, "persistence": "RAM only"} | WORKING_WITH_LIMITATIONS | RAM-backed; reset after restart. |
| Trace panel / filter | True | True | Developer trace panel loads real trace and filters events | {"real_trace_visible": true, "filter_works": true} | WORKING_WITH_LIMITATIONS | RAM-backed; reset after restart. |
| JSON export | True | True | JSON export downloads correct synthetic transcript | {"downloaded": true, "transcript_rows": 2, "storage": "postgres"} | PASS | — |
| Malformed response sample | True | True | Unknown/malformed 200 ChatResponse is handled without uncaught UI crash | {"malformed_200_did_not_crash": true, "malformed_products_type": "object"} | PASS | — |
| Responsive layout | True | True | Mobile390/tablet768/desktop1440 render without horizontal page overflow | {"viewports": [{"width": 390, "page_scroll_width": 390, "horizontal_overflow": false, "screenshot": "evidence/ui/viewport-390.png"}, {"width": 768, "page_scroll_width": 768, "horizontal_overflow": false, "screenshot": "evidence/ui/viewport-768.png"}, {"width": 1440, "page_scroll_width": 1440, "horizontal_overflow": false, "screenshot": "evidence/ui/viewport-1440.png"}]} | PASS | — |
| Keyboard / basic a11y | True | True | Basic labelled input, log live region and keyboard submit usable | {"labelled_textarea": true, "keyboard_submit": true, "transcript_live_region": "polite", "scope": "basic only; no WCAG certification"} | PASS | — |
| Console / page errors | True | True | No unexpected browser page errors during normal UI flows | {"unexpected_normal_pageerrors": 0, "controlled_malformed_errors": [], "console_count": 33} | PASS | — |
| Storefront integration inventory | False | True | Storefront navigation/widget availability is independently inventoried, not assumed | {"storefront_chatbot_lab_links": 0, "storefront_chat_widget_present": false, "classification": "NEW_FEATURE_REQUIRING_APPROVAL", "UI_STOREFRONT_READY": false} | NOT_IMPLEMENTED | New feature needs approval; direct Lab is not storefront chat. |

## Visual observations thật

[Mobile390](evidence/ui/top-390.png), [tablet768](evidence/ui/top-768.png), [desktop1440](evidence/ui/top-1440.png); [full page mobile](evidence/ui/viewport-390.png), [tablet](evidence/ui/viewport-768.png), [desktop](evidence/ui/viewport-1440.png). Browser measurements scrollWidth=viewport390/768/1440, không tràn ngang. Đã xem ảnh thực: mobile scenarios chuyển2cột và chat xuống dưới, cần scroll; tablet2cột rồi inspector bên dưới; desktop3cột với trace bên dưới. Trace42events làm trang mobile dài, vẫn đọc/scroll được. Đây là usability limitation của Lab, không phải bằng chứng storefront-ready. Keyboard Tab tới nút gửi/Enter hoạt động; log aria-live=polite; chưa audit WCAG đầy đủ.

[Browser errors/console](evidence/ui/browser_errors.json):0uncaught pageerrors, có console network401/404/controlled503; không tuyên bố zero console noise. [Supplemental attribution](evidence/ui/network-attribution.json) xác minh anonymous auth401 và fresh nonexistent session404; earlier generic404 chưa đủ URL để quy mọi cái về một cause.

## Known contract gaps / new features

- Historical text only: contract limitation; không dựng cards/sources từ text.
- Feedback/trace non-durable: contract limitation; mapping feedback -1/0/1 vs DB1–5 chưa duyệt.
- Missing storefront integration: new feature requiring approval, không thuộc implement scope.
- Malformed response test chỉ một sample incomplete200; không cam kết chịu mọi payload hỏng.
- Current Lab footer nói lịch sử postgres, RAM-feedback/trace đúng mode. Mutations và DEMO được gắn giới hạn rõ ràng. Production build flag/production infrastructure chưa phải runtime đã kiểm thử.

UI09hotfix được chứng minh bằng real browser retry+focused test. Session switch trong pendingPOST bị disable; stale close response được regression component test bảo vệ. Không đổi authentication/API routing.
