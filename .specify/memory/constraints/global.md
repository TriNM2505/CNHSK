# constraints/global.md — Ràng buộc KỸ THUẬT toàn cục

**Version:** 1.0.0 | **Cập nhật:** 2026-09-28 | **Maintainer:** Trí (leader)
**Enforcement:** Review thủ công + CI (chưa có linter tự động cho Java)

> Định nghĩa "**hợp lý của CNHSK**" thay vì để agent dùng "hợp lý" từ training data.
> Trả lời câu hỏi: **"Dùng công nghệ gì? Được và cấm thư viện nào?"**
> 📎 Canonical: constitution **ES-01** (stack), **ES-02** (thư viện FE), **ES-03** (naming),
> **HR-07** (query). Nếu lệch → constitution thắng, sync lại file này.

---

## 1. Technology Stack (bất biến trừ khi có RFC + cả nhóm duyệt)

| Layer | Công nghệ | Version |
|---|---|---|
| Backend | Spring Boot | **3.5.14** |
| Language | Java | **17 LTS** |
| Database | PostgreSQL | 16+ |
| Cache / Ranking | Redis | 8 |
| Migration | Flyway (`flyway-core` + `flyway-database-postgresql`) | built-in |
| Build | **Maven Wrapper** (`./mvnw`) | — |
| Auth | JWT `io.jsonwebtoken:jjwt` | **0.12.6** |
| ORM | Spring Data JPA (Hibernate) | built-in |
| Frontend web | React + TypeScript + Vite | **18.3 / 5.7 / 5** |
| CSS | Tailwind | **3.4** |
| Router | react-router-dom | **6.28** |
| Mobile | React Native + Expo | chốt khi bắt đầu |
| Game | Phaser hoặc PixiJS | chốt khi bắt đầu |
| Env | `me.paulschwarz:spring-dotenv` | 4.0.0 |

> **Lý do chọn:** đây là phiên bản nhóm **đã dùng thật** ở dự án trước (`Move_home`),
> không phải bản mới nhất. Spring Boot 4.x có trong `.m2` nhưng chưa từng dùng thật.

---

## 2. Naming Conventions

| Loại | Convention | Ví dụ |
|---|---|---|
| Java class / enum | PascalCase | `MasteryService`, `QuestionType` |
| Java method / variable | camelCase | `calculateMastery()`, `totalScore` |
| Package Java | lowercase dot, gốc `com.cnhsk` | `com.cnhsk.learning.service` |
| DB table | snake_case **số nhiều** | `questions`, `game_scores` |
| DB column | snake_case | `created_at`, `mastery_level` |
| REST endpoint | kebab-case, danh từ số nhiều | `/api/learning/vocabulary-sets` |
| React component | PascalCase | `LearningShell.tsx`, `FeatureCard.tsx` |
| CSS class | kebab-case (Tailwind utility ưu tiên) | `.btn-primary` |
| FE function | camelCase | `renderScoreboard()` |

**Ngôn ngữ:** comment kỹ thuật + tên biến/method = **tiếng Anh**.
Text hiển thị người dùng = **tiếng Việt CÓ DẤU**.

> ⚠️ Tránh từ khoá PostgreSQL khi đặt tên bảng (`user`, `order`, `group`…).
> Bài học từ `Move_home`: phải dùng `app_user` thay `user`. CNHSK đã tránh sẵn —
> schema `auth` dùng `users` (số nhiều nên không trùng).

---

## 3. Approved Packages (danh sách trắng)

**Spring Boot starters:** `web` · `data-jpa` · `data-redis` · `security` · `validation` · `websocket` · `mail`
**Runtime / tool:** `postgresql` (driver) · `spring-boot-devtools` (optional) · `org.projectlombok:lombok` (optional) · `me.paulschwarz:spring-dotenv` 4.0.0
**Auth:** `io.jsonwebtoken:jjwt-api` / `jjwt-impl` / `jjwt-jackson` 0.12.6
**Migration:** `org.flywaydb:flyway-core` + `flyway-database-postgresql`
**Test:** `spring-boot-starter-test` · `com.h2database:h2` · `spring-security-test` · ArchUnit
**Plugin build:** `spring-boot-maven-plugin` · `maven-compiler-plugin`
**Frontend:** `react` · `react-dom` · `react-router-dom` · `tailwindcss` · `lucide-react` · `clsx` · `tailwind-merge` · `vite` · `typescript`

---

## 4. Banned Packages (kèm LÝ DO)

| Cấm | Lý do |
|---|---|
| **MongoDB, bất kỳ DB thứ hai** | Phá AC-04 và AC-10 — mất transaction xuyên schema, là lợi thế chính khi gộp từ 3 database về 1. Chỗ cần linh hoạt dùng `JSONB` |
| MyBatis · jOOQ · raw JDBC nối chuỗi SQL | Chỉ Spring Data JPA + JPQL tham số hoá (**HR-07** — chống SQL injection) |
| `jjwt` 0.9.x · `com.auth0:java-jwt` | Chỉ dùng `jjwt` 0.12.x |
| `log4j-core` thêm trực tiếp | Dùng logback mặc định Spring — tránh CVE Log4Shell |
| PostgreSQL `ENUM type` · `hibernate-types` cho enum | Status dùng `VARCHAR` + `CHECK` (**AC-06**) |
| `double` / `float` cho tiền hoặc điểm | Tiền `NUMERIC(12,2)`, điểm `BIGINT` (**AC-07**) |
| Vue · Angular · Svelte · Next.js | Frontend là React 18 + Vite (**ES-01**) |
| Redux · MobX · Zustand | Chưa cần — dùng state của React. Thêm phải có lý do thật |
| Thư viện UI component (MUI, Ant Design, Chakra) | Tailwind + component tự viết. Thêm cần RFC |

> Danh sách này **mở** — leader bổ sung khi cần.

---

## 5. Quy trình thêm package mới

1. Agent **KHÔNG** tự thêm dependency vào `pom.xml` hay `package.json` — xem `safety.md`.
2. Đề xuất qua leader kèm lý do: vì sao cần, Spring hoặc thư viện hiện có làm được không.
3. Leader duyệt → thêm vào file build + cập nhật §3 của file này.

---

## 6. API-First

- Endpoint **MỚI**: viết hoặc cập nhật OpenAPI **TRƯỚC** khi implement → ít nhất một
  thành viên khác duyệt → mới code.
- Mọi task đụng API phải cập nhật spec **cùng PR**.
- Chưa bật CI chặn; nhóm tự giác tuân thủ.

> 📎 Canonical: constitution **AC-01**.

---

## 7. Ranh giới module (ArchUnit kiểm tự động)

Bốn module `auth` · `learning` · `community` · `shared` trong một ứng dụng.

| # | Quy tắc | Máy kiểm được? |
|---|---|---|
| 1 | Không khoá ngoại xuyên schema — `user_id` là `BIGINT` thường, không `REFERENCES auth.users` | ✅ ArchUnit |
| 2 | Không `JOIN` xuyên schema | ❌ Phải rà khi review |
| 3 | Không `@ManyToOne` xuyên module — dùng `Long userId` | ✅ ArchUnit |
| 4 | Entity ghi rõ schema: `@Table(name = "posts", schema = "community")` | ✅ ArchUnit |
| 5 | Lấy hồ sơ người dùng qua `UserLookup.findByIds()`, không truy vấn thẳng | ❌ Phải rà khi review |

> 📎 Canonical: constitution **AC-02**, **AC-03**.
