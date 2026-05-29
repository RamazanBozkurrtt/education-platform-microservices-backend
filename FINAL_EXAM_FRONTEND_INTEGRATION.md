# Final Exam Frontend Integration Contract

Bu dokuman `course-service` icindeki final exam API kontratini tek yerde toplar.
Frontend bu kontrata gore giderse mevcut validation sorunlari kapanir.

## 1) Base Bilgiler

- Base path: `http://localhost:8090/api/v1/courses`
- Controller path alias: backend `/courses` ve `/api/v1/courses` ikisini de mapliyor.
- Auth: tum endpointler `Authorization: Bearer <JWT>` ister.
- JSON endpointler icin `Content-Type: application/json`.
- Image upload endpointleri icin `multipart/form-data`.

## 2) Response Envelope

Tum response'lar `RestResponse<T>` formatinda doner:

```json
{
  "success": true,
  "status": 200,
  "message": "OK",
  "data": {},
  "errors": null,
  "timestamp": 1716760000000
}
```

Validation hatasinda:

```json
{
  "success": false,
  "status": 400,
  "message": "Validation failed",
  "errors": {
    "options[0].isCorrect": ["isCorrect is required"]
  }
}
```

## 3) Endpoint Listesi

### Instructor / Admin

1. `POST /{courseId}/final-exam`  
   Final exam olusturur.

2. `PUT /{courseId}/final-exam`  
   Final exam gunceller (tam update).

3. `GET /{courseId}/final-exam/manage`  
   Manage ekrani icin exam + question + option doner.

4. `DELETE /{courseId}/final-exam`  
   Exam'i soft-delete/deactivate eder.

5. `POST /{courseId}/final-exam/questions`  
   Soru ekler.

6. `PUT /{courseId}/final-exam/questions/{questionId}`  
   Soru gunceller.

7. `DELETE /{courseId}/final-exam/questions/{questionId}`  
   Soru siler.

8. `PUT /{courseId}/final-exam/questions/{questionId}/image`  
   Soru gorseli yukler/gunceller.

9. `POST /{courseId}/final-exam/questions/{questionId}/image`  
   Soru gorseli yukler (PUT ile ayni islev).

10. `DELETE /{courseId}/final-exam/questions/{questionId}/image`  
    Soru gorselini siler.

### Student

1. `GET /{courseId}/final-exam/overview`
2. `POST /{courseId}/final-exam/attempts`
3. `GET /{courseId}/final-exam/attempts/{attemptId}`
4. `PUT /{courseId}/final-exam/attempts/{attemptId}/answers`
5. `POST /{courseId}/final-exam/attempts/{attemptId}/submit`
6. `POST /{courseId}/final-exam/attempts/{attemptId}/terminate`

## 4) Request Kontratlari

## 4.1 FinalExamCreateRequest

```json
{
  "title": "Java Final Exam",
  "description": "Core Java + Spring",
  "passingScore": 70,
  "questionCount": 10,
  "durationMinutes": 30,
  "maxAttempts": 3,
  "availabilityDays": 7,
  "active": true
}
```

Kurallar:
- `title`: zorunlu, bos olamaz, max 255.
- `passingScore`: zorunlu, 0..100.
- `questionCount`: zorunlu, min 1.
- `durationMinutes`: zorunlu, min 1.
- `maxAttempts`: opsiyonel, min 1 (default 3).
- `availabilityDays`: opsiyonel, min 1 (default 3).
- `active`: opsiyonel (default true).

## 4.2 FinalExamUpdateRequest (kritik)

`PUT` oldugu icin tam body gonderilmeli:

```json
{
  "title": "Java Final Exam v2",
  "description": "Updated",
  "passingScore": 75,
  "questionCount": 12,
  "durationMinutes": 35,
  "maxAttempts": 3,
  "availabilityDays": 7,
  "active": true
}
```

Kurallar:
- Create'den farkli olarak `maxAttempts`, `availabilityDays`, `active` burada zorunlu.
- Bu 3 alan `null/undefined` ise 400 validation doner.

## 4.3 ExamQuestionCreate / Update

```json
{
  "questionText": "2 + 2 kactir?",
  "orderIndex": 0,
  "points": 1,
  "active": true,
  "options": [
    { "optionText": "3", "isCorrect": false, "orderIndex": 0 },
    { "optionText": "4", "isCorrect": true,  "orderIndex": 1 },
    { "optionText": "5", "isCorrect": false, "orderIndex": 2 },
    { "optionText": "6", "isCorrect": false, "orderIndex": 3 }
  ]
}
```

Question kurallari:
- `questionText`: zorunlu, max 10000.
- `orderIndex`: zorunlu, min 0.
- `points`: pozitif (gonderilmezse default 1).
- `active`: create ve update'te gonderin (update'te zorunlu).
- `options`: bos olamaz.

Option kurallari:
- `optionText`: zorunlu, max 5000.
- `isCorrect`: zorunlu boolean (`true/false`).
- `orderIndex`: zorunlu, min 0.

Business rule:
- En az 2 option olmali.
- Tam olarak 1 adet `isCorrect=true` olmali.

### Alias (geriye uyumluluk)
- `correct` da `isCorrect` olarak parse edilir.
- `text` de `optionText` olarak parse edilir.
- Standartta yine de `isCorrect` ve `optionText` kullanin.

## 4.4 SaveExamAnswerRequest

```json
{
  "answers": [
    { "questionId": 101, "selectedOptionId": 1001 },
    { "questionId": 102, "selectedOptionId": 1008 }
  ]
}
```

Kurallar:
- `answers`: bos olamaz.
- Her item'da `questionId` ve `selectedOptionId` zorunlu.
- `selectedOptionId` ilgili soruya ait degilse 400 doner.

## 4.5 SubmitExamRequest

Body opsiyonel ve bos olabilir:

```json
{}
```

veya body gondermeden `POST` atilabilir.

## 5) Response Model Notlari

`GET /manage` response'unda option alani:
- `correct` olarak doner (`isCorrect` degil).

Frontendte onerilen normalizasyon:
- API'den alirken: `correct -> isCorrect`.
- API'ye gonderirken: `isCorrect` kullan.

## 6) Kritik Is Kurallari (Frontendin bilmesi gereken)

1. Aktif soru sayisi `questionCount` degerini gecemez.  
   Aktif soru eklerken/guncellerken limit doluysa validation/business error gelir.

2. Final exam update'te `questionCount` mevcut aktif soru sayisindan dusuk olamaz.

3. Ogrenci attempt baslatma icin:
- Kurs published olmali.
- Ogrenci enrolled olmali.
- Course completion policy saglanmis olmali.
- Final exam "ready" olmali (aktif soru sayisi = questionCount, her soruda 2+ option, tek dogru).
- Kalan deneme hakki olmali.

4. Attempt bitis zamani backendde:
- `min(startedAt + durationMinutes, startedAt + availabilityDays)`

5. Submit score hesabi:
- `score = (dogru_sayisi / toplam_soru) * 100` (2 basamak, HALF_UP)
- `score >= passingScore` ise passed.

## 7) Final Exam Error Code Rehberi

- `3001 VALIDATION_ERROR (400)`: DTO/field veya is kurali ihlali.
- `7010 FINAL_EXAM_NOT_FOUND (404)`
- `7011 FINAL_EXAM_NOT_ACTIVE (400)`
- `7012 FINAL_EXAM_ATTEMPTS_EXHAUSTED (409)`
- `7013 FINAL_EXAM_ACTIVE_ATTEMPT_EXISTS (409)`
- `7014 FINAL_EXAM_ATTEMPT_NOT_FOUND (404)`
- `7015 FINAL_EXAM_ATTEMPT_INVALID_STATE (409)`
- `7016 FINAL_EXAM_COURSE_NOT_COMPLETED (403)`
- `7017 FINAL_EXAM_NOT_READY (400)`
- `7018 FINAL_EXAM_ALREADY_EXISTS (409)`

## 8) Frontend Pre-Submit Checklist

Exam create/update:
- `title`, `passingScore`, `questionCount`, `durationMinutes` dolu mu?
- Update'te ekstra: `maxAttempts`, `availabilityDays`, `active` mutlaka var mi?

Question create/update:
- Tum optionlarda `isCorrect` boolean mi?
- Tek bir `isCorrect=true` var mi?
- En az 2 option var mi?
- `orderIndex` alanlari numeric ve `>=0` mi?

Attempt answers:
- Her `selectedOptionId`, ilgili `questionId` option listesinde var mi?

## 9) Frontend Tip Onerisi (TS)

```ts
export type ExamOptionPayload = {
  optionText: string;
  isCorrect: boolean;
  orderIndex: number;
};

export type FinalExamPayload = {
  title: string;
  description?: string | null;
  passingScore: number;
  questionCount: number;
  durationMinutes: number;
  maxAttempts: number;
  availabilityDays: number;
  active: boolean;
};
```

## 10) Bu dokumanin amaci

Bu kontrat birebir backend kodundan cikartildi.  
Frontend requestleri bu modele sabitlenirse `VALIDATION_ERROR` kaynakli final exam problemleri buyuk oranda kapanir.

