# React Exception Handling Brief (Codex Input)

Bu dokuman, React tarafinda exception handling mimarisini hizli kurdurmak icin minimum gereksinimi verir.

## 1) Hedef
- Tum API/network/UI hatalarini tek bir `AppError` modelinde toplamak.
- 401/403/429/5xx durumlarini merkezi ve tutarli yonetmek.
- Validation hatalarini form alanlarina map etmek.
- Gateway ve microservice hata format farkini tek noktada normalize etmek.

## 2) Backend Hata Sozlesmesi (mevcut durum)
### A) Service cevabi (auth/user/course/enrollment)
```json
{
  "success": false,
  "status": 400,
  "message": "Validation failed",
  "data": null,
  "errors": { "fieldName": ["message1"] },
  "timestamp": 1710000000000
}
```

Notlar:
- Validation hatasinda `errors` alani genelde `Record<string, string[]>`.
- Cogu business/internal hatada `errors` bos olabilir.
- `HTTP status` asil kaynaktir; bazi endpointlerde body `status` ile uyumsuzluk olabilir.

### B) API Gateway security cevabi
```json
{
  "error": "UNAUTHORIZED",
  "message": "Invalid issuer",
  "path": "/api/v1/users/me"
}
```

Not:
- Bu formatta `success/status/timestamp/errors` yok.

## 3) React'te kurulacak katmanlar
- `src/shared/http/client.ts`: Tek HTTP client (axios/fetch wrapper) + response interceptor.
- `src/shared/errors/normalizeApiError.ts`: Tum hata formatlarini `AppError`'a ceviren parser.
- `src/shared/errors/types.ts`: `AppError` tipleri.
- `src/shared/errors/handleAppError.ts`: Uygulama genel davranis (toast, redirect, logout).
- `src/app/ErrorBoundary.tsx`: Render-time exception fallback.
- Form adaptor: `validation.fieldErrors` -> `react-hook-form setError`.

## 4) Canonical AppError modeli
```ts
export type AppErrorKind =
  | "validation"
  | "auth"
  | "forbidden"
  | "rate_limit"
  | "not_found"
  | "server"
  | "network"
  | "unknown";

export interface AppError {
  kind: AppErrorKind;
  message: string;
  httpStatus?: number;
  code?: string;
  fieldErrors?: Record<string, string[]>;
  raw?: unknown;
}
```

## 5) Davranis Kurallari
- `401`: 1 kez refresh dene; basarisizsa logout + login sayfasina yonlendir.
- `403`: yetki hatasi bildir (sayfada veya toast).
- `429`: rate limit mesaji goster, otomatik retry yapma.
- `5xx`: generic hata mesaji + opsiyonel error tracking.
- `network/cors/timeout`: kullaniciya baglanti hatasi mesaji.
- Is kurali icin `message` parse etmeye guvenme; once status/kind kullan.

## 6) Kabul Kriterleri
1. UI tarafinda ham backend hata objesi render edilmez.
2. Tum API cagrilari ayni normalizerdan gecerek `AppError` uretir.
3. Form validasyon hatalari ilgili input altinda gorunur.
4. 401 akisi tek yerde (interceptor veya auth layer) kontrol edilir.
5. ErrorBoundary runtime exceptionlarda beyaz ekran yerine fallback UI gosterir.

## Referans Backend Dosyalari
- `commonCore/src/main/java/com/edubase/commonCore/utils/RestResponse.java`
- `commonCore/src/main/java/com/edubase/commonCore/exceptions/ErrorCode.java`
- `auth-service/src/main/java/com/edubase/auth/exceptions/handling/GlobalExceptionHandler.java`
- `user-service/src/main/java/com/edubase/user/handler/GlobalExceptionHandler.java`
- `course-service/src/main/java/com/edubase/course/handler/GlobalExceptionHandler.java`
- `enrollment-service/src/main/java/com/edubase/enrollment/handler/GlobalExceptionHandler.java`
- `api-gateway/src/main/java/com/edubase/gateway/security/SecurityErrorWriter.java`
