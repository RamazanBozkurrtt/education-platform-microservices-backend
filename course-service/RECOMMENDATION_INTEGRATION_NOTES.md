# Recommendation Integration Notes

## 2026-05-30

- `RecommendationServiceClient` FastAPI cagri path'leri preferred versiyonlu endpointlere alindi:
  - `POST /api/v1/recommendations/dashboard`
  - `POST /api/v1/recommendations/search`
- `recommendation.service.base-url` konfigurasyonu ayni sekilde kullanilmaya devam ediyor.
- `RecommendationFacadeService` loglari netlestirildi:
  - Basarili durumda: `Received recommendations from recommendation-service`
  - Fallback durumunda: `Using fallback recommendations because recommendation-service ...`
