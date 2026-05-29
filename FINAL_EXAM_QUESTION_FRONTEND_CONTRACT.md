# Final Exam - Soru Ekleme API Kontrati (Frontend)

Bu dokuman, `POST /api/v1/courses/{courseId}/final-exam/questions` endpointine soru eklerken frontend tarafinda gonderilmesi gereken request yapisini netlestirir.

## 1) Endpoint

- **Method:** `POST`
- **Path:** `/api/v1/courses/{courseId}/final-exam/questions`
- **Auth:** `Authorization: Bearer <JWT>`
- **Content-Type:** `application/json`

## 2) Request Body (Beklenen Model)

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

## 3) Zorunlu Kurallar

### Question seviyesinde

- `questionText`: zorunlu, bos olamaz.
- `orderIndex`: zorunlu, `>= 0`.
- `active`: zorunlu (`true/false`).
- `points`: `> 0` olmali (gondermiyorsan backend varsayilan `1` kullanir).
- `options`: zorunlu, en az 1 eleman degil; is kurali geregi en az 2 olmasi gerekir.

### Option seviyesinde

Her option icin:
- `optionText`: zorunlu, bos olamaz.
- `isCorrect`: zorunlu, **boolean** olmali (`true` veya `false`).
- `orderIndex`: zorunlu, `>= 0`.

### Is kurali (business rule)

- `options` icinde **tam olarak 1 adet** `isCorrect: true` olmali.
- 0 adet veya 2+ adet dogru secenek olursa request reject edilir (`400`).

## 4) Logdaki Hatanin Karsiligi

Asagidaki hata:

- `options[x].isCorrect: rejected value [null]`

su anlama gelir:

- Frontend bu alanlari hic gondermuyor, ya da `null/undefined` uretiyor.
- Backend her option icin `isCorrect` bekliyor ve `null` kabul etmiyor.

## 5) Frontend Tarafinda Yapilmasi Gereken Mapleme

UI modeli ne olursa olsun API'ye cikmadan once payload kesin olarak su sekle maplenmeli:

```ts
type ExamOptionPayload = {
  optionText: string;
  isCorrect: boolean;
  orderIndex: number;
};

type ExamQuestionPayload = {
  questionText: string;
  orderIndex: number;
  points?: number;
  active: boolean;
  options: ExamOptionPayload[];
};
```

Ornek map:

```ts
const payload: ExamQuestionPayload = {
  questionText: form.questionText.trim(),
  orderIndex: form.orderIndex,
  points: form.points ?? 1,
  active: form.active ?? true,
  options: form.options.map((o, i) => ({
    optionText: (o.optionText ?? "").trim(),
    isCorrect: Boolean(o.isCorrect),
    orderIndex: o.orderIndex ?? i
  }))
};
```

Not: `Boolean(o.isCorrect)` kullanimi, `undefined` degerleri `false` yapar. Bu nedenle submit oncesi "tam olarak 1 true var mi?" kontrolu mutlaka yapilmali.

## 6) Submit Oncesi Minimum FE Validasyon Checklist

1. `questionText` dolu mu?
2. `options.length >= 2` mi?
3. Tum optionlarda `optionText` dolu mu?
4. Tum optionlarda `isCorrect` boolean mi?
5. `isCorrect === true` olan secenek sayisi tam olarak 1 mi?
6. `orderIndex` alanlari sayisal ve `>= 0` mi?

Bu kontroller backend'e gitmeden yapilmazsa 400 doner.

## 7) Geçici Geriye Uyumluluk Notu

Backend su alias alanlari da parse edebilir:
- `correct` -> `isCorrect`
- `text` -> `optionText`

Ama kalici standart alan adlari:
- `optionText`
- `isCorrect`
- `orderIndex`

Frontend kodunda standart alan adlari kullanilmali.

## 8) Hata Durumunda FE Debug Onerisi

Network tabinda request body'yi kontrol edin. `options` elemanlarinda her birinde kesinlikle `isCorrect` var mi, `null` mi, yok mu bakilmali.

Ornek:

```ts
console.log("final-exam add-question payload", JSON.stringify(payload, null, 2));
```

