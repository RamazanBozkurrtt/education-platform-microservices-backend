# EduBase Recommendation Service (FastAPI)

EduBase bitirme projesi icin gelistirilmis, `course-service` tarafindan cagrilan semantic + progress-aware kurs onerisi mikroservisidir.

Bu servis:
- Spring Boot `course-service` contract alan adlari ile birebir uyumludur.
- `candidateCourses` + `userProfile` verisi uzerinden skorlar.
- `sentence-transformers` ile semantic benzerlik kullanir.
- Model yuklenemezse lexical fallback ile calismaya devam eder.

## API Endpoints

- `GET /health` -> `{"status":"UP"}`
- `POST /api/v1/recommendations/dashboard`
- `POST /api/v1/recommendations/search`

Uyumluluk icin legacy path'ler de desteklenir:
- `POST /recommendations/dashboard`
- `POST /recommendations/search`

## Contract Clarification (Gateway vs AI Service)

This FastAPI service (`ai-service`) is an internal scoring service. It expects a full JSON payload
(`userProfile` + `candidateCourses`) and therefore uses `POST` for recommendation endpoints.

- Internal AI service contract:
  - `POST /api/v1/recommendations/dashboard`
  - `POST /api/v1/recommendations/search`
  - `Content-Type: application/json`
  - No JWT validation is done inside this service.

Frontend should not call `ai-service` directly for dashboard recommendations with `GET`.
Frontend should call the public Course API endpoint through gateway:

- Public frontend contract (via gateway -> course-service):
  - `GET /api/v1/recommendations/dashboard?limit=6`
  - `Authorization: Bearer <JWT>`

If `GET /api/v1/recommendations/dashboard` is routed to `ai-service`, `405 Method Not Allowed`
is expected because `ai-service` endpoint is `POST` by design.

## Contract Uyumlulugu

Response sekli:

```json
{
  "recommendations": [
    {
      "courseId": "c101",
      "score": 0.91,
      "reason": "Son ilgi alanlarına semantik olarak benzediği için önerildi.",
      "badges": ["Semantic Match", "Backend", "INTERMEDIATE", "Recommended"]
    }
  ],
  "strategy": "SEMANTIC_SIMILARITY + PROGRESS_AWARE_RANKING"
}
```

## Skorlama Stratejisi

Dashboard:

`final_score = 0.40 * semantic + 0.25 * category + 0.15 * duration + 0.10 * level + 0.10 * popularity`

Search:

`final_score = 0.55 * semantic + 0.15 * category + 0.10 * duration + 0.10 * level + 0.10 * popularity`

Kurallar:
- `completedCourseIds` icindeki kurslar atlanir.
- `courseId` bos/null kurslar atlanir.
- `dropoutRate >= 60` iken kisa kurslara bonus verilir.
- `preferredDurationSeconds` yoksa `7200` varsayilir.
- Skorlar `0..1` araliginda tutulur.

## Local Calistirma (Windows)

```powershell
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Test:
- Health: `http://localhost:8000/health`
- Swagger: `http://localhost:8000/docs`

## Docker

```bash
docker build -t edubase-recommendation-service .
docker run --rm -p 8000:8000 --name recommendation-service edubase-recommendation-service
```

## /docs Uzerinden Test

1. `http://localhost:8000/docs` ac.
2. `POST /api/v1/recommendations/dashboard` veya `search` endpointini sec.
3. `Try it out` ile payload'i gir ve `Execute` yap.

## Dashboard Ornek Payload

```json
{
  "userId": "123",
  "limit": 10,
  "context": "DASHBOARD",
  "userProfile": {
    "favoriteCategories": ["Java", "Backend"],
    "averageCompletionRate": 42.5,
    "dropoutRate": 61.2,
    "preferredDurationSeconds": 5400,
    "completedCourseIds": ["1", "2"],
    "inProgressCourseIds": ["3"],
    "recentlyWatchedCourseIds": ["3", "5"],
    "preferredLevels": ["BEGINNER", "INTERMEDIATE"]
  },
  "candidateCourses": [
    {
      "courseId": "c101",
      "title": "Spring Boot Security",
      "description": "Learn JWT authentication and authorization with Spring Boot.",
      "category": "Backend",
      "level": "INTERMEDIATE",
      "tags": ["Java", "Spring Boot", "Security", "JWT"],
      "durationSeconds": 7200,
      "lessonCount": 18,
      "rating": 4.7,
      "enrollmentCount": 1200,
      "createdAt": "2026-05-01T10:00:00Z"
    }
  ]
}
```

## Search Ornek Payload

```json
{
  "userId": "123",
  "query": "spring security",
  "limit": 10,
  "context": "SEARCH",
  "userProfile": {
    "favoriteCategories": ["Java", "Backend"],
    "averageCompletionRate": 42.5,
    "dropoutRate": 61.2,
    "preferredDurationSeconds": 5400,
    "completedCourseIds": ["1", "2"],
    "inProgressCourseIds": ["3"],
    "recentlyWatchedCourseIds": ["3", "5"],
    "preferredLevels": ["BEGINNER", "INTERMEDIATE"]
  },
  "candidateCourses": [
    {
      "courseId": "c101",
      "title": "Spring Boot Security",
      "description": "Learn JWT authentication and authorization with Spring Boot.",
      "category": "Backend",
      "level": "INTERMEDIATE",
      "tags": ["Java", "Spring Boot", "Security", "JWT"],
      "durationSeconds": 7200,
      "lessonCount": 18,
      "rating": 4.7,
      "enrollmentCount": 1200,
      "createdAt": "2026-05-01T10:00:00Z"
    }
  ]
}
```

## cURL Ornekleri

Dashboard:

```bash
curl -X POST "http://localhost:8000/api/v1/recommendations/dashboard" \
  -H "Content-Type: application/json" \
  -d @dashboard.json
```

Search:

```bash
curl -X POST "http://localhost:8000/api/v1/recommendations/search" \
  -H "Content-Type: application/json" \
  -d @search.json
```

## Kubernetes Test Komutlari

Gateway pod icinden direct ai-service testi (`POST` beklenir):

```bash
curl -i -X POST "http://ai-service:8000/api/v1/recommendations/dashboard" \
  -H "Content-Type: application/json" \
  -d '{
    "userId":"u1",
    "limit":6,
    "context":"DASHBOARD",
    "userProfile":{"favoriteCategories":["Backend"]},
    "candidateCourses":[{"courseId":"c101","title":"Spring Boot Security","category":"Backend"}]
  }'
```

Gateway uzerinden public endpoint testi (`course-service` contract, `GET`):

```bash
curl -i "http://localhost:30090/api/v1/recommendations/dashboard?limit=6" \
  -H "Authorization: Bearer <JWT>"
```

## Notlar

- Servis dis baglantiya (DB, queue) ihtiyac duymaz; tum hesaplama request icerigindeki candidate listesiyle yapilir.
- Semantic model yuklenemezse loglarda fallback bilgisi gorulur ve servis yanit vermeye devam eder.
