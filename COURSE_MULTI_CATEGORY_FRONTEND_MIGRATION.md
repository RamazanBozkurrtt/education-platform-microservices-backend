# Course Service Multi-Category Migration (Frontend)

Bu dokuman, `course-service` tarafinda kursun tek kategori (`categoryId`) modelinden coklu kategori (`categoryIds`) modeline gecis icin frontend entegrasyon notlarini verir.

## Ozet

- **Request tarafi (create/update):** artik `categoryId` yerine `categoryIds: string[]` gonderilmeli.
- **Response tarafi:** yeni alanlar `categoryIds` ve `categories` eklendi.
- **Geriye donuk uyumluluk:** response icinde `categoryId` ve `category` halen doner; bunlar `categoryIds[0]` / `categories[0]` olarak set edilir (deprecated).

## Etkilenen Endpointler

1. `POST /api/v1/courses`
2. `PUT /api/v1/courses/{id}`
3. `GET /api/v1/courses/public/{id}`
4. `GET /api/v1/courses/public`
5. `GET /api/v1/courses/{id}`
6. `GET /api/v1/courses`
7. `GET /api/v1/courses/me`
8. `GET /api/v1/courses/public/categories` (degismedi, secim kaynagi olarak kullanilmaya devam)

## Request Degisikligi

## Once (eski)
```json
{
  "title": "Java Backend",
  "description": "Comprehensive backend training",
  "price": 1499.0,
  "categoryId": "681f7f2f1e4db80aaf9fa9f1",
  "learningOutcomes": ["A", "B", "C", "D"],
  "tags": ["java", "spring"]
}
```

## Sonra (yeni)
```json
{
  "title": "Java Backend",
  "description": "Comprehensive backend training",
  "price": 1499.0,
  "categoryIds": [
    "681f7f2f1e4db80aaf9fa9f1",
    "681f7f2f1e4db80aaf9fa9f2"
  ],
  "learningOutcomes": ["A", "B", "C", "D"],
  "tags": ["java", "spring"]
}
```

## Validation Kurallari

- `categoryIds` zorunlu.
- En az 1, en fazla 5 kategori.
- Her eleman bos olamaz.
- Her eleman max 100 karakter.
- Gonderilen tum kategori id'leri `categories` koleksiyonunda mevcut olmali; aksi halde `COURSE_CATEGORY_NOT_FOUND`.

## Response Modeli

Yeni response alani:
- `categoryIds: string[]`
- `categories: CategoryResponse[]`

Deprecated ama halen gelen:
- `categoryId: string | null` (primary category)
- `category: CategoryResponse | null` (primary category detail)

Ornek:
```json
{
  "id": "course-1",
  "title": "Java Backend",
  "categoryIds": ["cat-1", "cat-2"],
  "categories": [
    { "id": "cat-1", "categoryName": "Backend" },
    { "id": "cat-2", "categoryName": "Architecture" }
  ],
  "categoryId": "cat-1",
  "category": { "id": "cat-1", "categoryName": "Backend" }
}
```

## Frontend Yapilacaklar (Checklist)

1. Course create/edit formunda kategori inputunu `single-select` yerine `multi-select` yap.
2. Form state:
   - `categoryId: string` -> `categoryIds: string[]`
3. API payload mapperlarini guncelle:
   - create/update requestlerinde `categoryIds` gonder.
4. Response mapperlarini guncelle:
   - Ekranda kategori etiketlerini `categories[]` veya `categoryIds[]` uzerinden render et.
5. Geriye donuk guvenli okuma (opsiyonel ama onerilir):
   - `categoryIds` bos gelirse `categoryId` degerinden tek elemanli liste uret.
6. Validation/UI:
   - En az 1 kategori secimi zorunlu.
   - Maksimum 5 secim.
7. Filtre ve liste ekranlarinda birden fazla kategori gosterimini destekle.

## TypeScript Ornekleri

```ts
export interface CourseCreateRequest {
  title: string;
  description: string;
  price: number;
  categoryIds: string[];
  learningOutcomes: string[]; // exactly 4
  tags?: string[];
}

export interface CategoryResponse {
  id: string;
  categoryName: string;
}

export interface CourseResponse {
  id: string;
  title: string;
  description: string;
  price: number;
  categoryIds: string[];
  categories: CategoryResponse[];
  categoryId?: string | null; // deprecated compatibility
  category?: CategoryResponse | null; // deprecated compatibility
}
```

## QA Senaryolari

1. Tek kategori secimi ile create/update basarili.
2. Iki veya daha fazla kategori secimi ile create/update basarili.
3. `categoryIds=[]` -> 400 validation.
4. `categoryIds` icinde olmayan id -> `COURSE_CATEGORY_NOT_FOUND`.
5. Course detail/list endpointlerinde `categoryIds` ve `categories` dolu geliyor.
6. Eski ekranda sadece `categoryId` kullanan alanlar primary category ile bozulmadan calisiyor.
