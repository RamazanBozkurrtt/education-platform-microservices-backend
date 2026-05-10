# Course Management API Integration (React Panel)

Bu dokuman, kurs yonetim paneli icin course-service tarafindaki guncel update/delete/video akisini verir.

## Base URL

- Gateway: `http://localhost:8090`
- Tum endpointler JWT Bearer token ister (public endpointler haric).

## 1) Video Guncelleme (Lesson Video Upload)

- **Method:** `PUT`
- **Path:** `/api/v1/courses/{courseId}/lessons/{lessonId}/video`
- **Content-Type:** `multipart/form-data`
- **Body:** `file` (mp4)

### Beklenen Davranis

- Video MinIO'ya yazilir.
- Mongo `courses` dokumaninda ilgili lesson icin:
  - `videoUrl` guncellenir: `/courses/{courseId}/lessons/{lessonId}/video`
  - `videoUpdatedAt` guncellenir (yeni alan)
  - Cozulebilirse `duration` guncellenir
- Course `updatedAt` alanina yeni timestamp yazilir.

### Ornek cURL

```bash
curl -X PUT "http://localhost:8090/api/v1/courses/{courseId}/lessons/{lessonId}/video" \
  -H "Authorization: Bearer <TOKEN>" \
  -F "file=@lesson.mp4;type=video/mp4"
```

---

## Kategori Listesi (Dropdown Kaynagi)

- **Method:** `GET`
- **Path:** `/api/v1/courses/public/categories`
- **Auth:** Gerekmez

### Ornek Response

```json
{
  "success": true,
  "data": [
    { "id": "681f7f2f1e4db80aaf9fa9f1", "categoryName": "Web Gelistirme" },
    { "id": "681f7f2f1e4db80aaf9fa9f2", "categoryName": "Veri Bilimi ve Yapay Zeka" }
  ]
}
```

Not: Kurs create/update requestindeki `categoryId` degeri, bu endpointten donen `id` olmalidir.

---

## 2) Kurs Bilgisi Guncelleme

- **Method:** `PUT`
- **Path:** `/api/v1/courses/{id}`
- **Content-Type:** `application/json`

### Request Body

```json
{
  "title": "Java Backend Masterclass",
  "description": "Comprehensive backend training",
  "price": 1499.00,
  "categoryId": "backend",
  "learningOutcomes": [
    "Outcome 1",
    "Outcome 2",
    "Outcome 3",
    "Outcome 4"
  ],
  "tags": ["java", "spring", "microservices"]
}
```

---

## 3) Ders Bilgisi Guncelleme

- **Method:** `PUT`
- **Path:** `/api/v1/courses/{courseId}/lessons/{lessonId}`
- **Content-Type:** `application/json`

### Request Body

```json
{
  "title": "Dependency Injection",
  "summaryTitle": "DI Basics",
  "videoUrl": "/courses/{courseId}/lessons/{lessonId}/video",
  "duration": 420,
  "orderIndex": 1,
  "completed": false
}
```

Not: `PUT` tam guncelleme oldugu icin gerekli alanlari eksiksiz gonderin.

---

## 4) Ders Silme

- **Method:** `DELETE`
- **Path:** `/api/v1/courses/{courseId}/lessons/{lessonId}`

### Beklenen Davranis

- Ders kurs listesinden silinir.
- Derse ait video objesi MinIO'dan temizlenir.

---

## 5) Kurs Silme

- **Method:** `DELETE`
- **Path:** `/api/v1/courses/{id}`

### Beklenen Davranis

- Kurs Mongo'dan silinir.
- Kurs resmi ve tum lesson videolari MinIO'dan temizlenir.

---

## 6) Video Silme (Sadece Video Kaldirma)

- **Method:** `DELETE`
- **Path:** `/api/v1/courses/{courseId}/lessons/{lessonId}/video`

### Beklenen Davranis

- MinIO'dan video silinir.
- Mongo'da ilgili lesson:
  - `videoUrl = null`
  - `videoUpdatedAt = null`
  - `duration = null`
- Course `updatedAt` yenilenir.

---

## Frontend Uygulama Notlari

1. Video upload basarili olduktan sonra ilgili kursu `GET /api/v1/courses/{id}` ile yeniden fetch edin.
2. `lessonId` olarak `orderIndex` degil, lesson nesnesinin gercek `id` alanini kullanin.
3. `PUT` endpointlerinde zorunlu alanlari eksik gondermeyin.
4. Silme isleminden sonra local state'i optimistic degilse sunucudan yeniden senkronlayin.
