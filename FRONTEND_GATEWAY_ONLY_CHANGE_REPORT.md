# Frontend Change Report - Gateway Only Access

## Scope
- Date: 2026-06-01
- Goal: Frontend'in mikroservislere dogrudan degil, sadece API Gateway uzerinden erismesini zorunlu hale getirmek.

## Backend Changes Applied
- File: `docker-compose.yml`
- Applied change: Tum backend mikroservislerde host `ports` publish kaldirildi, `expose` kullanildi.
- Public olarak acik birakilan tek backend giris noktasi:
  - `api-gateway` -> `http://localhost:8090`

### Services closed to host direct access
- `auth-service` (`8080`)
- `user-service` (`8081`, `9091`)
- `course-service` (`8083`, `9092`)
- `enrollment-service` (`8084`)
- `payment-service` (`8087`, `9095`)
- `review-service` (`8086`)
- `search-service` (`8085`, `9094`)

## Frontend Impact
- Artik `http://localhost:8080/8081/8083/8084/8085/8086/8087` URL'leri frontend tarafinda kullanilamaz.
- Frontend tek bir API base URL kullanmalidir:
  - `http://localhost:8090`

## Gateway Route Contract (Frontend)
- Auth: `/api/v1/auth/**`
- User: `/api/v1/users/**`
- Instructor: `/api/v1/instructors/**`
- Course: `/api/v1/courses/**` (ve legacy `/courses/**`)
- Enrollment: `/api/v1/enrollments/**`
- Payment: `/api/v1/payments/**`
- Review: `/api/v1/reviews/**`
- Recommendation: `/api/v1/recommendations/**`

## Required Frontend Actions (Best Practice)
1. Tek bir `API_BASE_URL` env kullanin (`http://localhost:8090`).
2. Servis-bazli base URL konfigurasyonlarini kaldirin.
3. HTTP client katmaninda tum istekleri tek gateway client uzerinden gecirin.
4. 401/403/429/5xx hata yonetimini merkezi interceptor/normalizer katmaninda tutun.
5. Swagger / API contract dogrulamasini gateway uzerinden yapin (`/swagger-ui`).

## Validation Performed
- `docker compose config` komutu basariyla calisti.
- Compose ciktisinda mikroservislerde `ports` yerine `expose` goruluyor; `api-gateway:8090` publish durumda.

## Notes
- Bu degisiklik frontend erisimi icin gateway zorunlulugu getirir.
- Container agi icindeki servisler arasi HTTP/gRPC iletisim etkilenmez.
