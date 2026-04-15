# Service Communication Map

Bu dosya projedeki servisler arasi iletisim yapisini, hangi yerde `Kafka`, hangi yerde `gRPC` kullanildigini ve request/event akislarini aciklar.

Amac:
- Hangi servisin kime bagli oldugunu tek bakista gormek
- Senkron ve asenkron iletisim sinirlarini netlestirmek
- Yeni servis eklerken mevcut mimariyi bozmadan nasil entegre olunacagini gostermek

## 1. Genel Mimari Ozeti

Projede iki tip servisler arasi iletisim var:

1. `gRPC`
- Senkron dogrulama ve query amacli kullaniliyor.
- Bir servis, diger servise "bu veri var mi?" veya "bu entity su durumda mi?" diye soruyor.
- Kisa timeout ile calisiyor.
- Hata durumunda cagrilan servis `BusinessException(INTERNAL_ERROR)` gibi kontrollu hata donduruyor.

2. `Kafka`
- Asenkron domain event yayini icin kullaniliyor.
- Bir servis kendi transaction'i basariyla commit olduktan sonra event publish ediyor.
- Event alan servis bu bilgiye gore kendi lokal modelini guncelleyebiliyor veya ileride yeni subscriber'lar eklenebiliyor.

Bu projede su anda genel kural:
- `gRPC` = validation/query
- `Kafka` = event propagation/integration

## 2. Servisler ve Roller

### `auth-service`
- Kullanici kaydi ve authentication islemlerini yonetir.
- Yeni user olustugunda event uretir.

### `user-service`
- Kullanici profili/veri modeli tarafini tutar.
- `auth-service`ten gelen `user.registered` event'ini tuketir.
- Diger servislere user existence bilgisini `gRPC` ile saglar.

### `course-service`
- Kurs verisini tutar.
- Kurs olusturma tarafinda instructor icin `user-service`e `gRPC` ile sorar.
- Diger servislere course existence/published bilgisini `gRPC` ile saglar.

### `enrollment-service`
- Kullanici-kurs kayitlarini tutar.
- Enrollment olustururken hem user'i hem course'u `gRPC` ile dogrular.
- Enrollment olusunca ve iptal olunca Kafka event yayar.

### `api-gateway`
- Dis dunyadan gelen HTTP isteklerinin giris noktasi.
- Servisler arasi `Kafka` veya `gRPC` akisinda aktif rol almaz.
- Sadece HTTP route ve security gateway gorevi gorur.

## 3. gRPC Haritasi

## 3.1 Proto Contract'lar

Ortak proto dosyalari:
- [commonGrpcContracts/src/main/proto/user_query.proto](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/commonGrpcContracts/src/main/proto/user_query.proto)
- [commonGrpcContracts/src/main/proto/course_query.proto](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/commonGrpcContracts/src/main/proto/course_query.proto)

### `UserQueryService`
Saglayan servis:
- `user-service`

Method:
- `GetUserByAuthId(UserByAuthIdRequest) -> UserByAuthIdResponse`

Saglanan bilgi:
- `authUserId`
- `exists`
- `email`

### `CourseQueryService`
Saglayan servis:
- `course-service`

Method:
- `GetCourseById(CourseByIdRequest) -> CourseByIdResponse`

Saglanan bilgi:
- `courseId`
- `exists`
- `published`
- `instructorId`
- `title`

## 3.2 gRPC Server'lar

### `user-service gRPC server`
Dosyalar:
- [user-service/src/main/java/com/edubase/user/grpc/UserQueryGrpcService.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/user-service/src/main/java/com/edubase/user/grpc/UserQueryGrpcService.java)
- [user-service/src/main/java/com/edubase/user/grpc/GrpcServerRunner.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/user-service/src/main/java/com/edubase/user/grpc/GrpcServerRunner.java)

Port:
- `9091` (`grpc.server.port`, default)

Davranis:
- `authUserId` ile `UserProfileRepository.findByAuthUserId(...)` yapar.
- Kayit varsa `exists=true`, yoksa `exists=false`.

### `course-service gRPC server`
Dosyalar:
- [course-service/src/main/java/com/edubase/course/grpc/CourseQueryGrpcService.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/course-service/src/main/java/com/edubase/course/grpc/CourseQueryGrpcService.java)
- [course-service/src/main/java/com/edubase/course/grpc/GrpcServerRunner.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/course-service/src/main/java/com/edubase/course/grpc/GrpcServerRunner.java)

Port:
- `9092` servis ici
- Docker host mapping: `9093 -> 9092`

Davranis:
- `courseId` ile `CourseRepository.findById(...)` yapar.
- Kayit varsa `exists=true`.
- Kurs `PUBLISHED` ise `published=true`.

## 3.3 gRPC Client'lar

### `course-service -> user-service`
Dosyalar:
- [course-service/src/main/java/com/edubase/course/configuration/grpc/GrpcClientConfig.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/course-service/src/main/java/com/edubase/course/configuration/grpc/GrpcClientConfig.java)
- [course-service/src/main/java/com/edubase/course/grpc/UserGrpcClient.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/course-service/src/main/java/com/edubase/course/grpc/UserGrpcClient.java)

Amac:
- Instructor rolundeki user kurs olustururken, user profili gercekten var mi diye kontrol etmek.

Akis:
1. `CourseServiceImpl.createCourse(...)` cagrilir.
2. Role `INSTRUCTOR` ise `userGrpcClient.assertUserExists(authContext.userId())` cagrilir.
3. `user-service`den `exists=true` gelirse kurs olusur.
4. `exists=false` ise `USER_NOT_FOUND`.
5. gRPC baglanti/cagri hatasi varsa `INTERNAL_ERROR`.

Kod:
- [course-service/src/main/java/com/edubase/course/service/concretes/CourseServiceImpl.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/course-service/src/main/java/com/edubase/course/service/concretes/CourseServiceImpl.java)

### `enrollment-service -> user-service`
Dosyalar:
- [enrollment-service/src/main/java/com/edubase/enrollment/configuration/grpc/GrpcClientConfig.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/enrollment-service/src/main/java/com/edubase/enrollment/configuration/grpc/GrpcClientConfig.java)
- [enrollment-service/src/main/java/com/edubase/enrollment/grpc/UserGrpcClient.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/enrollment-service/src/main/java/com/edubase/enrollment/grpc/UserGrpcClient.java)

Amac:
- Enrollment olusturulmadan once user'in sistemde gercekten var oldugunu dogrulamak.

### `enrollment-service -> course-service`
Dosyalar:
- [enrollment-service/src/main/java/com/edubase/enrollment/configuration/grpc/GrpcClientConfig.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/enrollment-service/src/main/java/com/edubase/enrollment/configuration/grpc/GrpcClientConfig.java)
- [enrollment-service/src/main/java/com/edubase/enrollment/grpc/CourseGrpcClient.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/enrollment-service/src/main/java/com/edubase/enrollment/grpc/CourseGrpcClient.java)

Amac:
- Enrollment olusturulmadan once course var mi ve `PUBLISHED` durumda mi diye kontrol etmek.

## 3.4 Enrollment Olusturma Sirasinda gRPC Akisi

Kod merkezi:
- [enrollment-service/src/main/java/com/edubase/enrollment/service/concretes/EnrollmentServiceImpl.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/enrollment-service/src/main/java/com/edubase/enrollment/service/concretes/EnrollmentServiceImpl.java)

Akis:
1. Client `POST /api/v1/enrollments` cagirir.
2. `api-gateway` istegi `enrollment-service`e yonlendirir.
3. `EnrollmentServiceImpl.createEnrollment(...)` calisir.
4. Auth context'ten actor user id okunur.
5. Hedef user belirlenir.
6. `userGrpcClient.assertUserExists(targetUserId)` cagrilir.
7. `courseGrpcClient.assertCoursePublished(courseId)` cagrilir.
8. Lokal DB'de `findByUserIdAndCourseId(...)` ile duplicate kontrol edilir.
9. Kayit yoksa enrollment `ACTIVE` olarak olusur.
10. Transaction commit sonrasinda Kafka event publish edilir.

Bu tasarim neden mantikli:
- User ve course ownership/source of truth ayri servislerde.
- Enrollment kendi DB'sine "kopya cache" tutmak yerine source servislere soruyor.
- Veri tutarliligi burada "runtime validation" ile korunuyor.

## 4. Kafka Haritasi

## 4.1 Kafka Topic'ler

Su an aktif topic'ler:
- `user.registered.v1`
- `enrollment.created.v1`
- `enrollment.cancelled.v1`

Config kaynaklari:
- [auth-service/src/main/resources/application.yml](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/auth-service/src/main/resources/application.yml)
- [user-service/src/main/resources/application.yml](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/user-service/src/main/resources/application.yml)
- [enrollment-service/src/main/resources/application.yml](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/enrollment-service/src/main/resources/application.yml)

## 4.2 Kafka Event Tipleri

Ortak event class'lari:
- [commonCore/src/main/java/com/edubase/commonCore/events/UserRegisteredEvent.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/commonCore/src/main/java/com/edubase/commonCore/events/UserRegisteredEvent.java)
- [commonCore/src/main/java/com/edubase/commonCore/events/EnrollmentCreatedEvent.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/commonCore/src/main/java/com/edubase/commonCore/events/EnrollmentCreatedEvent.java)
- [commonCore/src/main/java/com/edubase/commonCore/events/EnrollmentCancelledEvent.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/commonCore/src/main/java/com/edubase/commonCore/events/EnrollmentCancelledEvent.java)

## 4.3 Producer ve Consumer Haritasi

### Topic: `user.registered.v1`

Producer:
- `auth-service`

Dosyalar:
- [auth-service/src/main/java/com/edubase/auth/messaging/UserRegisteredEventRelay.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/auth-service/src/main/java/com/edubase/auth/messaging/UserRegisteredEventRelay.java)
- [auth-service/src/main/java/com/edubase/auth/messaging/UserRegisteredKafkaPublisher.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/auth-service/src/main/java/com/edubase/auth/messaging/UserRegisteredKafkaPublisher.java)

Consumer:
- `user-service`

Dosya:
- [user-service/src/main/java/com/edubase/user/messaging/UserRegisteredEventConsumer.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/user-service/src/main/java/com/edubase/user/messaging/UserRegisteredEventConsumer.java)

Akis:
1. `auth-service` icinde register islemi basarili olur.
2. `AuthenticationServiceImpl.register(...)` domain event publish eder.
3. `@TransactionalEventListener(AFTER_COMMIT)` ile Kafka publish edilir.
4. `user-service` event'i tuketir.
5. `UserProfile` kaydi yoksa olusturur, varsa `authUserId/email` bagini gunceller.

Kod merkezi:
- [auth-service/src/main/java/com/edubase/auth/service/concretes/AuthenticationServiceImpl.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/auth-service/src/main/java/com/edubase/auth/service/concretes/AuthenticationServiceImpl.java)

Bu event neden var:
- `auth-service` ve `user-service` veritabanlari ayri.
- Kullanici kimlik/auth bilgisi ile profile bilgisi loosely coupled tutuluyor.
- Event ile eventual consistency saglaniyor.

### Topic: `enrollment.created.v1`

Producer:
- `enrollment-service`

Dosyalar:
- [enrollment-service/src/main/java/com/edubase/enrollment/messaging/EnrollmentEventRelay.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/enrollment-service/src/main/java/com/edubase/enrollment/messaging/EnrollmentEventRelay.java)
- [enrollment-service/src/main/java/com/edubase/enrollment/messaging/EnrollmentKafkaPublisher.java](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/enrollment-service/src/main/java/com/edubase/enrollment/messaging/EnrollmentKafkaPublisher.java)

Consumer:
- Su an projede aktif consumer yok.

Beklenen gelecek kullanimlar:
- `notification-service`: "Kaydiniz olusturuldu" email/push
- `analytics-service`: enrollment metrics
- `progress-service`: ilk progress kaydi olusturma
- `audit-service`: domain audit stream

### Topic: `enrollment.cancelled.v1`

Producer:
- `enrollment-service`

Consumer:
- Su an projede aktif consumer yok.

Beklenen gelecek kullanimlar:
- Notification
- Audit
- Analytics

## 4.4 Enrollment Event Publish Akisi

### Enrollment create
Akis:
1. Enrollment DB'ye yazilir.
2. Aynı transaction icinde domain event Spring event olarak publish edilir.
3. `EnrollmentEventRelay` bunu `AFTER_COMMIT` fazinda dinler.
4. Kafka producer `enrollment.created.v1` topic'ine event basar.

Neden `AFTER_COMMIT`:
- DB rollback olursa event yayinlanmasin.
- "DB'de yok ama event cikti" problemi azaltilsin.

### Enrollment cancel
Akis:
1. Enrollment status `CANCELLED` olur.
2. DB save yapilir.
3. Commit sonrasi `enrollment.cancelled.v1` basilir.

## 5. Uctan Uca Akislar

## 5.1 User Register Flow

```text
Client
  -> api-gateway
  -> auth-service
  -> auth DB save
  -> AFTER_COMMIT event
  -> Kafka: user.registered.v1
  -> user-service consumer
  -> user DB upsert profile
```

Bu akis asenkron.

Sonuc:
- Register request basarili donse bile `user-service` tarafindaki profile birkac ms/sn sonra olusabilir.

## 5.2 Course Create Flow

```text
Client
  -> api-gateway
  -> course-service
  -> gRPC: user-service / GetUserByAuthId
  -> course-service DB save
```

Bu akis senkron.

Sonuc:
- User dogrulanmadan kurs olusmaz.

## 5.3 Enrollment Create Flow

```text
Client
  -> api-gateway
  -> enrollment-service
  -> gRPC: user-service / GetUserByAuthId
  -> gRPC: course-service / GetCourseById
  -> enrollment DB save
  -> AFTER_COMMIT event
  -> Kafka: enrollment.created.v1
```

Bu akis hibrit:
- Validation kismi senkron (`gRPC`)
- Integration/event kismi asenkron (`Kafka`)

## 6. Runtime Port ve Network Haritasi

Docker compose tarafinda ilgili portlar:
- `user-service HTTP`: `8081`
- `user-service gRPC`: container `9091`, host `9091`
- `course-service HTTP`: `8083`
- `course-service gRPC`: container `9092`, host `9093`
- `enrollment-service HTTP`: `8084`
- `Kafka host`: `9092`
- `Kafka internal broker`: `kafka:29092`

Kaynak:
- [docker-compose.yml](/C:/Users/ramaz/source/repos/education-platform-microservices-backend/docker-compose.yml)

Not:
- `course-service` gRPC host mapping'i `9093` yapildi cunku `9092` Kafka tarafinda kullaniliyor.
- Container'lar birbirine host port ile degil container internal port ile erisir.

## 7. Tasarim Kararlari

### Neden `user-service` verisi Kafka ile tasiniyor ama enrollment verisi gRPC ile soruluyor?

Cevap:
- `user.registered` olayi bir "state propagation" problemi.
- `enrollment create` ise bir "anlik validation" problemi.

Yani:
- "Yeni user olustu, bunu diger bounded context de bilsin" -> `Kafka`
- "Bu user su anda var mi?" -> `gRPC`

### Neden `course-service`e Kafka ile course event publish etmedik?

Su anki ihtiyac sadece:
- enrollment olusurken kursun varligini ve publish durumunu kontrol etmek

Bunun icin event tasimaya gerek yok.

Eger ileride:
- enrollment tarafinda course title snapshot tutulacaksa
- analytics icin bagimsiz local read model istenecekse
- course publish/unpublish event'leri dinlenecekse

o zaman `course.created`, `course.published`, `course.unpublished` gibi topic'ler eklenebilir.

## 8. Su Anda Sistemdeki Bagimliliklar

Senkron bagimliliklar:
- `course-service` -> `user-service`
- `enrollment-service` -> `user-service`
- `enrollment-service` -> `course-service`

Asenkron bagimliliklar:
- `auth-service` -> Kafka -> `user-service`
- `enrollment-service` -> Kafka -> gelecek subscriber'lar

## 9. Failure Modelleri

### gRPC failure
Eger `user-service` veya `course-service` erisilemezse:
- cagirilan islem fail olur
- servis `INTERNAL_ERROR` dondurur

Etkisi:
- `course create` bloklanir
- `enrollment create` bloklanir

Bu tradeoff bilincli:
- invalid user/course ile enrollment yaratmamak daha onemli

### Kafka failure
Kafka publish DB commit sonrasinda fail olursa:
- ana business transaction basarili olabilir
- integration event kacabilir

Su anki davranis:
- log atiliyor
- retry/outbox mekanizmasi henuz yok

Bu onemli teknik not:
- Su anki yapi "best effort publish"
- Tam production-grade garanti icin `Transactional Outbox` eklenmeli

## 10. Gelistirme Icin Onerilen Sonraki Adimlar

1. `Outbox pattern`
- Ozellikle `user.registered` ve `enrollment.*` event'leri icin

2. `Consumer` ekleme
- `notification-service`
- `analytics-service`
- `audit-service`

3. `gRPC resilience`
- retry policy
- circuit breaker
- request correlation id propagation

4. `Event versioning policy`
- su an topic isimlerinde `v1` var, bu iyi
- payload degisikliklerinde backward compatibility korunmali

## 11. Hızlı Ozet

Su an sistemde ana akislar:
- `auth-service` user register eder -> `Kafka` ile `user-service` profile modelini besler
- `course-service` kurs olustururken `gRPC` ile `user-service`e user sorar
- `enrollment-service` enrollment olustururken `gRPC` ile hem `user-service` hem `course-service`e sorar
- `enrollment-service` create/cancel sonrasi `Kafka` event publish eder

Tek cumlelik mimari ozeti:
- `gRPC` ile anlik dogrulama yapiliyor, `Kafka` ile domain event'ler sisteme yayiliyor.
