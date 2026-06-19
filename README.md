# EduBase Education Platform Backend

EduBase, online eğitim platformu senaryosu için geliştirilmiş mikroservis tabanlı bir backend projesidir. Proje; kimlik doğrulama, kullanıcı profilleri, eğitmen akışları, kurs yönetimi, kayıt/enrollment, ödeme, yorum, arama indeksleme, medya depolama ve kurs önerileri gibi temel alanları ayrı servisler halinde ele alır.

Backend servisleri ağırlıklı olarak Spring Boot 3.2.3 ve Java 17 ile geliştirilmiştir. Kurs önerisi için ayrıca FastAPI tabanlı bir Python servisi bulunur. Servisler REST, gRPC ve Kafka eventleri ile haberleşir.

## İçerik

- [Mimari Özeti](#mimari-özeti)
- [Teknoloji Yığını](#teknoloji-yığını)
- [Gerekli Kütüphaneler ve Bağımlılıklar](#gerekli-kütüphaneler-ve-bağımlılıklar)
- [Proje Yapısı](#proje-yapısı)
- [Servisler ve Portlar](#servisler-ve-portlar)
- [Docker Compose ile Çalıştırma](#docker-compose-ile-çalıştırma)
- [Kubernetes ile Çalıştırma](#kubernetes-ile-çalıştırma)
- [API Dokümantasyonu](#api-dokümantasyonu)
- [Ortam Değişkenleri](#ortam-değişkenleri)
- [Veri ve Kalıcılık](#veri-ve-kalıcılık)
- [Test ve Build](#test-ve-build)
- [Sorun Giderme](#sorun-giderme)

## Mimari Özeti

```text
Client / Frontend
      |
      v
API Gateway :8090
      |
      +--> auth-service        -> PostgreSQL, Redis, Kafka
      +--> user-service        -> PostgreSQL, Redis, Kafka, MinIO, gRPC
      +--> course-service      -> MongoDB, PostgreSQL projection DB, Redis, Kafka, MinIO, gRPC
      +--> enrollment-service  -> PostgreSQL, Kafka, gRPC clients
      +--> payment-service     -> PostgreSQL, Kafka, gRPC server/client
      +--> review-service      -> PostgreSQL, course-service
      +--> search-service      -> Elasticsearch, Kafka, gRPC
      +--> recommendation-service / ai-service -> FastAPI internal recommendation scoring
```

Genel akış:

- Dış dünyaya açılan ana giriş noktası `api-gateway` servisidir.
- Servisler arası senkron isteklerde REST ve gRPC kullanılır.
- Domain eventleri için Kafka kullanılır.
- JWT tabanlı authentication ve role bazlı authorization uygulanır.
- Medya dosyaları MinIO üzerinde saklanır.
- Kurs verileri MongoDB'de, ilişkisel projection ve diğer domain verileri PostgreSQL'de tutulur.
- Arama indeksleme için Elasticsearch ve `search-service` kullanılır.

## Teknoloji Yığını

| Alan | Teknoloji |
| --- | --- |
| Ana backend | Java 17, Spring Boot 3.2.3 |
| Gateway | Spring Cloud Gateway |
| Build | Maven multi-module |
| Veritabanı | PostgreSQL 15, MongoDB 7 |
| Cache / token state | Redis 7 |
| Event streaming | Kafka, Zookeeper |
| Arama | Elasticsearch 8.13.4 |
| Medya depolama | MinIO |
| Servisler arası RPC | gRPC, Protocol Buffers |
| API dokümantasyonu | Springdoc OpenAPI / Swagger UI |
| Recommendation service | Python 3.11, FastAPI, sentence-transformers |
| Container | Docker, Docker Compose |
| Orkestrasyon | Kubernetes |

## Gerekli Kütüphaneler ve Bağımlılıklar

Projeyi çalıştırmak için iki farklı yol desteklenir. Docker/Docker Compose kullanıldığında Java, Maven, Python ve servis bağımlılıkları container imajları içinde hazırlanır. Lokal geliştirme yapılacaksa aşağıdaki araçların sistemde kurulu olması gerekir.

### Sistem gereksinimleri

| Bağımlılık | Gerekli sürüm / açıklama |
| --- | --- |
| Java JDK | 17 |
| Maven | 3.9.x veya uyumlu güncel sürüm |
| Docker | Docker Engine veya Docker Desktop |
| Docker Compose | v2 |
| Kubernetes CLI | `kubectl` |
| Lokal Kubernetes | Minikube, kind veya Docker Desktop Kubernetes |
| Python | 3.11, yalnızca `recommendation-service` lokal çalıştırılacaksa |

### Java / Spring Boot bağımlılıkları

Java servisleri Maven multi-module yapıdadır ve bağımlılıklar root [pom.xml](pom.xml) ile servis bazlı `pom.xml` dosyalarından yönetilir. Başlıca kullanılan kütüphaneler:

- Spring Boot Starter Web, Validation, Security, Data JPA, Data MongoDB, Data Redis
- Spring Cloud Gateway
- Spring Kafka
- Springdoc OpenAPI / Swagger UI
- PostgreSQL JDBC Driver
- Flyway Migration
- Lombok
- MapStruct
- JJWT
- gRPC ve Protocol Buffers
- MinIO Java SDK
- Elasticsearch client bağımlılıkları
- JUnit, Mockito ve Spring Boot Test

Maven bağımlılıklarını indirmek ve projeyi derlemek için:

```bash
mvn clean package
```

### Python / Recommendation service bağımlılıkları

`recommendation-service` Python/FastAPI tabanlıdır. Bağımlılıklar [recommendation-service/requirements.txt](recommendation-service/requirements.txt) dosyasında tutulur:

- FastAPI
- Uvicorn
- Pydantic
- HTTPX
- sentence-transformers
- NumPy
- scikit-learn

Lokal Python ortamını hazırlamak için:

```bash
cd recommendation-service
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

### Altyapı bağımlılıkları

Docker Compose veya Kubernetes ortamında aşağıdaki altyapı bileşenleri otomatik ayağa kaldırılır:

- PostgreSQL 15
- MongoDB 7
- Redis 7
- Kafka ve Zookeeper
- Elasticsearch 8.13.4
- MinIO

## Proje Yapısı

```text
.
|-- api-gateway/                 # Dış trafik, routing, rate limiting, Swagger aggregation
|-- auth-service/                # Login, register, JWT, refresh token, password reset
|-- user-service/                # Kullanıcı profili, avatar, eğitmen profili
|-- course-service/              # Kurs, kategori, seviye, ders, final sınavı, medya, öneriler
|-- enrollment-service/          # Kursa kayıt ve erişim kontrolü
|-- payment-service/             # Ödeme, fatura, payment eventleri
|-- review-service/              # Kurs yorumları ve puanlama
|-- search-service/              # Kurs arama indeksleri ve Kafka consumer
|-- recommendation-service/      # FastAPI tabanlı dahili AI/öneri servisi
|-- commonCore/                  # Ortak DTO, event ve yardımcı sınıflar
|-- commonJpa/                   # Ortak JPA entity altyapısı
|-- commonGrpcContracts/         # Proto dosyaları ve gRPC contractları
|-- k8s/                         # Kubernetes manifestleri
|-- init-db/                     # Lokal DB init dosyaları
|-- docker-compose.yml           # Lokal container ortamı
|-- pom.xml                      # Maven parent POM
```

## Servisler ve Portlar

### HTTP servisleri

| Servis | Docker/K8s container port | Host erişimi |
| --- | ---: | --- |
| api-gateway | 8090 | Docker: `localhost:8090`, K8s NodePort: `localhost:30090` |
| auth-service | 8080 | Gateway üzerinden |
| user-service | 8081 | Gateway üzerinden |
| course-service | 8083 | Gateway üzerinden |
| enrollment-service | 8084 | Gateway üzerinden |
| search-service | 8085 | İç servis |
| review-service | 8086 | Gateway üzerinden |
| payment-service | 8087 | Gateway üzerinden |
| recommendation-service | 8000 | İç servis, opsiyonel |

### gRPC servisleri

| Servis | Docker profili | Kubernetes profili |
| --- | ---: | ---: |
| user-service | 9091 | 9091 |
| course-service | 9092 | 9093 |
| search-service | 9094 | 9094 |
| payment-service | 9095 | 9095 |

> Not: `course-service` gRPC portu Docker Compose ortamında `9092`, Kubernetes ortamında `9093` olarak ayarlanmıştır. İlgili application profile dosyaları bu farkı yansıtır.

### Altyapı portları

| Bileşen | Docker host portu | Açıklama |
| --- | ---: | --- |
| PostgreSQL auth DB | 5432 | `auth_db` |
| PostgreSQL user DB | 5433 | `edubase_user_db` |
| PostgreSQL enrollment DB | 5434 | `edubase_enrollment_db` |
| PostgreSQL course projection DB | 5435 | `edubase_course_projection_db` |
| PostgreSQL review DB | 5436 | `edubase_review_db` |
| PostgreSQL payment DB | 5437 | `edubase_payment_db` |
| Redis | 6379 | Cache/token/rate limit |
| Kafka | 9092 | Host erişimi |
| MongoDB | 27017 | Course DB |
| Elasticsearch | 9200 | Search index |
| MinIO API | 9000 | Object storage |
| MinIO Console | 9001 | Web console |

## Docker Compose ile Çalıştırma

Docker Compose, lokal geliştirme için en hızlı çalışma yöntemidir. Bu yöntem PostgreSQL, MongoDB, Redis, Kafka, Elasticsearch, MinIO ve Spring Boot servislerini tek komutla ayağa kaldırır.

### Gereksinimler

- Docker Desktop veya Docker Engine
- Docker Compose v2
- Git
- En az 8 GB RAM önerilir. Elasticsearch ve Kafka nedeniyle daha düşük kaynaklarda ilk açılış yavaş olabilir.

### 1. Ortam değişkenlerini hazırlayın

Root dizindeki `.env` dosyası Docker Compose tarafından otomatik okunur. Lokal ortam için aşağıdaki minimum değerler yeterlidir:

```env
JWT_SECRET=<uzun-rastgele-jwt-secret>
INTERNAL_API_KEY=<uzun-rastgele-internal-api-key>
MINIO_ROOT_USER=<minio-root-user>
MINIO_ROOT_PASSWORD=<guclu-minio-root-password>
USER_MEDIA_MINIO_ACCESS_KEY=<user-media-minio-access-key>
USER_MEDIA_MINIO_SECRET_KEY=<user-media-minio-secret-key>
COURSE_MEDIA_MINIO_ACCESS_KEY=<course-media-minio-access-key>
COURSE_MEDIA_MINIO_SECRET_KEY=<course-media-minio-secret-key>
```

Bu değerleri gerçek ortamda güçlü ve rastgele üretin. `.env`, Kubernetes Secret veya benzeri dosyalardaki gerçek secret değerlerini README, issue, commit mesajı veya public dokümantasyona yazmayın.

### 2. Servisleri build edip başlatın

```bash
docker compose up --build -d
```

İlk çalıştırmada Maven dependency indirme, image build ve Elasticsearch/Kafka açılışı nedeniyle süre uzayabilir.

### 3. Durumu kontrol edin

```bash
docker compose ps
docker compose logs -f api-gateway
```

Gateway health kontrolü:

```bash
curl http://localhost:8090/actuator/health
```

Swagger UI:

```text
http://localhost:8090/swagger-ui
```

MinIO Console:

```text
http://localhost:9001
```

### 4. Logları servis bazlı izleme

```bash
docker compose logs -f auth-service
docker compose logs -f course-service
docker compose logs -f kafka
```

### 5. Ortamı durdurma

Containerları durdurup verileri korumak için:

```bash
docker compose down
```

Containerları ve volume verilerini tamamen silmek için:

```bash
docker compose down -v
```

`down -v` komutu PostgreSQL, MongoDB, Elasticsearch ve MinIO verilerini de siler.

### 6. Recommendation service'i Docker ile opsiyonel çalıştırma

`course-service`, `RECOMMENDATION_SERVICE_BASE_URL` için Docker profilinde varsayılan olarak `http://recommendation-service:8000` adresini bekler. Root `docker-compose.yml` içinde recommendation service tanımı yoksa kurs önerileri fallback algoritma ile çalışmaya devam eder. AI tabanlı servisi ayrıca çalıştırmak için:

```bash
cd recommendation-service
docker build -t edubase/recommendation-service:latest .
docker run --rm -p 8000:8000 --name recommendation-service edubase/recommendation-service:latest
```

Lokal FastAPI dokümantasyonu:

```text
http://localhost:8000/docs
```

Docker Compose network'ü içinden `course-service` tarafından erişilebilir olması için bu servisin Compose dosyasına eklenmesi veya aynı Docker network'e dahil edilmesi gerekir.

## Kubernetes ile Çalıştırma

Kubernetes manifestleri `k8s/` dizinindedir. Varsayılan namespace `edubase` olarak tanımlanmıştır.

### Gereksinimler

- Kubernetes cluster: Minikube, Docker Desktop Kubernetes, kind veya uzak cluster
- `kubectl`
- Docker
- En az 10 GB boş disk ve 8 GB RAM önerilir

### 1. Backend imajlarını build edin

Kubernetes manifestleri backend imajlarını `edubase/<service>:latest` olarak bekler. Lokal cluster kullanıyorsanız önce imajları build edin.

PowerShell:

```powershell
$services = @(
  "auth-service",
  "user-service",
  "api-gateway",
  "course-service",
  "enrollment-service",
  "payment-service",
  "review-service",
  "search-service"
)

foreach ($service in $services) {
  docker build -t "edubase/$service`:latest" -f "$service/Dockerfile" .
}

docker build -t edubase/recommendation-service:latest .\recommendation-service
```

Bash:

```bash
for service in auth-service user-service api-gateway course-service enrollment-service payment-service review-service search-service; do
  docker build -t "edubase/${service}:latest" -f "${service}/Dockerfile" .
done

docker build -t edubase/recommendation-service:latest ./recommendation-service
```

Minikube kullanıyorsanız imajları Minikube Docker daemon'ı içinde build etmek daha pratiktir:

```bash
eval $(minikube docker-env)
```

Windows PowerShell için:

```powershell
minikube docker-env | Invoke-Expression
```

Ardından yukarıdaki build komutlarını çalıştırın.

kind kullanıyorsanız imajları cluster'a yükleyin:

```bash
kind load docker-image edubase/auth-service:latest
kind load docker-image edubase/user-service:latest
kind load docker-image edubase/api-gateway:latest
kind load docker-image edubase/course-service:latest
kind load docker-image edubase/enrollment-service:latest
kind load docker-image edubase/payment-service:latest
kind load docker-image edubase/review-service:latest
kind load docker-image edubase/search-service:latest
kind load docker-image edubase/recommendation-service:latest
```

### 2. Secret değerlerini kontrol edin

`k8s/02-secret.yaml` dosyası güvenlik için placeholder değerler içerir. Kubernetes'e deploy etmeden önce bu değerleri güçlü, rastgele ve ortama özel secret değerleriyle değiştirin.

Değiştirilmesi gereken başlıca alanlar:

- `JWT_SECRET`
- `INTERNAL_API_KEY`
- `POSTGRES_PASSWORD`
- Servis bazlı DB şifreleri
- `MINIO_ROOT_USER`
- `MINIO_ROOT_PASSWORD`
- `GMAIL_USERNAME`
- `GMAIL_APP_PASSWORD`
- `PAYMENT_GATEWAY_WEBHOOK_SECRET`

### 3. Manifestleri sırayla uygulayın

```bash
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/01-configmap.yaml
kubectl apply -f k8s/02-secret.yaml
kubectl apply -f k8s/03-infra-core.yaml
kubectl apply -f k8s/04-backend-services.yaml
```

Arama altyapısını da çalıştırmak için:

```bash
kubectl apply -f k8s/05-optional-search.yaml
```

Recommendation service'i Kubernetes üzerinde çalıştırmak için:

```bash
kubectl apply -f recommendation-service/k8s/ai-service.yaml
```

> Not: `course-service` varsayılan olarak `RECOMMENDATION_SERVICE_BASE_URL=http://recommendation-service:8000` değerini beklerken mevcut recommendation manifesti Kubernetes servis adını `ai-service` olarak tanımlıyor. Bu nedenle iki seçenekten birini kullanın:
>
> - `course-service` için `RECOMMENDATION_SERVICE_BASE_URL=http://ai-service:8000` ortam değişkenini ekleyin.
> - Ya da recommendation servisinin Kubernetes Service adını `recommendation-service` olacak şekilde düzenleyin.

### 4. Pod durumlarını izleyin

```bash
kubectl get pods -n edubase -w
```

Detaylı servis listesi:

```bash
kubectl get deploy,svc,pvc -n edubase
```

Belirli bir servis logu:

```bash
kubectl logs -f deployment/api-gateway -n edubase
kubectl logs -f deployment/course-service -n edubase
```

### 5. Gateway'e erişin

`api-gateway` Kubernetes manifestinde NodePort olarak `30090` portuna açılır:

```text
http://localhost:30090
```

Swagger UI:

```text
http://localhost:30090/swagger-ui
```

Cluster tipine göre NodePort doğrudan çalışmıyorsa port-forward kullanın:

```bash
kubectl port-forward svc/api-gateway 8090:8090 -n edubase
```

Sonrasında:

```text
http://localhost:8090/swagger-ui
```

MinIO Console'a erişmek için:

```bash
kubectl port-forward svc/minio 9001:9001 -n edubase
```

Sonrasında:

```text
http://localhost:9001
```

### 6. Kubernetes ortamından kaldırma

Sadece uygulama kaynaklarını silmek için:

```bash
kubectl delete namespace edubase
```

Namespace silindiğinde bu namespace altindaki deployment, service, secret, configmap ve PVC kaynakları da silinir. Kalıcı volume davranışı cluster'in storage class ayarlarina bağlıdır.

## API Dokümantasyonu

Gateway Swagger UI, servislerin OpenAPI dokümanlarını tek arayüzde toplar:

Docker:

```text
http://localhost:8090/swagger-ui
```

Kubernetes:

```text
http://localhost:30090/swagger-ui
```

Gateway üzerinden yönlendirilen ana endpoint grupları:

| Endpoint | Servis |
| --- | --- |
| `/api/v1/auth/**` | auth-service |
| `/api/v1/users/**` | user-service |
| `/api/v1/instructors/**` | user-service |
| `/api/v1/courses/**` | course-service |
| `/courses/**` | course-service legacy path |
| `/api/v1/enrollments/**` | enrollment-service |
| `/api/v1/payments/**` | payment-service |
| `/api/v1/reviews/**` | review-service |
| `/api/v1/recommendations/**` | course-service |
| `/api/v1/media/**` | course-service |

OpenAPI JSON endpointleri:

| Doküman | URL |
| --- | --- |
| Auth | `/v3/api-docs/auth` |
| User | `/v3/api-docs/user` |
| Course | `/v3/api-docs/course` |
| Enrollment | `/v3/api-docs/enrollment` |
| Payment | `/v3/api-docs/payment` |
| Review | `/v3/api-docs/review` |

## Ortam Değişkenleri

En önemli ortak değişkenler:

| Değişken | Açıklama |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `docker`, `k8s`, `local`, `dev`, `prod` gibi Spring profili |
| `JWT_SECRET` | JWT imzalama anahtarı |
| `JWT_ISSUER` | Token issuer değeri |
| `JWT_AUDIENCE` | Token audience değeri |
| `INTERNAL_API_KEY` | Servisler arası internal endpoint koruması |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker adresi |
| `REDIS_HOST`, `REDIS_PORT` | Redis bağlantı bilgileri |
| `MONGODB_URI` | Course MongoDB bağlantı adresi |
| `ELASTICSEARCH_URIS` | Elasticsearch adresi |
| `GATEWAY_URL` / `API_GATEWAY_URL` | Gateway internal URL |
| `CORS_ALLOWED_ORIGINS` | Frontend origin listesi |
| `RECOMMENDATION_SERVICE_BASE_URL` | Course service'in dahili recommendation servisine gideceği base URL |

Servis bazlı DB değişkenleri:

| Servis | Değişkenler |
| --- | --- |
| auth-service | `AUTH_DB_URL`, `AUTH_DB_USERNAME`, `AUTH_DB_PASSWORD` |
| user-service | `USER_DB_URL`, `USER_DB_USERNAME`, `USER_DB_PASSWORD` |
| course-service | `COURSE_PROJECTION_DB_URL`, `COURSE_PROJECTION_DB_USERNAME`, `COURSE_PROJECTION_DB_PASSWORD`, `MONGODB_URI` |
| enrollment-service | `ENROLLMENT_DB_URL`, `ENROLLMENT_DB_USERNAME`, `ENROLLMENT_DB_PASSWORD` |
| payment-service | `PAYMENT_DB_URL`, `PAYMENT_DB_USERNAME`, `PAYMENT_DB_PASSWORD` |
| review-service | `REVIEW_DB_URL`, `REVIEW_DB_USERNAME`, `REVIEW_DB_PASSWORD` |

Medya depolama:

| Değişken | Açıklama |
| --- | --- |
| `MINIO_ROOT_USER` | MinIO admin kullanıcısı |
| `MINIO_ROOT_PASSWORD` | MinIO admin şifresi |
| `USER_MEDIA_MINIO_ENDPOINT` | User media için MinIO endpoint |
| `COURSE_MEDIA_MINIO_ENDPOINT` | Course media için MinIO endpoint |
| `USER_MEDIA_MINIO_BUCKET` | User medya bucket adı |
| `COURSE_MEDIA_MINIO_BUCKET` | Course medya bucket adı |

## Veri ve Kalıcılık

Docker Compose ortamında veriler Docker volume'lerinde tutulur:

- `auth_postgres_data`
- `user_postgres_data`
- `enrollment_postgres_data`
- `payment_postgres_data`
- `review_postgres_data`
- `course_projection_postgres_data`
- `course_mongo_data`
- `elasticsearch_data`
- `minio_data`

Kubernetes ortamında manifestler PVC kullanır:

- `postgres-data`
- `mongo-data`
- `minio-data`
- `elasticsearch-data`

Migration'lar Spring servisleri içindeki Flyway migration dosyaları ile çalışır. Örneğin:

- `auth-service/src/main/resources/db/migration`
- `user-service/src/main/resources/db/migration`
- `enrollment-service/src/main/resources/db/migration`
- `payment-service/src/main/resources/db/migration`
- `review-service/src/main/resources/db/migration`

## Test ve Build

Tüm Maven modüllerini test etmek için:

```bash
mvn clean test
```

Tüm projeyi paketlemek için:

```bash
mvn clean package
```

Tek bir servisi ve bağımlı modüllerini build etmek için:

```bash
mvn -pl course-service -am clean package
```

Testleri atlayarak hızlı package almak için:

```bash
mvn -pl course-service -am clean package -DskipTests
```

Recommendation service'i lokal çalıştırmak için:

```bash
cd recommendation-service
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

## Sorun Giderme

### Gateway açılıyor ama istekler 5xx dönüyor

Servislerin hazır olup olmadığını kontrol edin:

```bash
docker compose ps
docker compose logs -f api-gateway
docker compose logs -f auth-service
```

Kubernetes:

```bash
kubectl get pods -n edubase
kubectl logs deployment/api-gateway -n edubase
```

### Kafka geç açılıyor

Kafka ve Zookeeper ilk açılışta zaman alabilir. Docker Compose healthcheck tamamlanana kadar backend servisleri bekler.

```bash
docker compose logs -f kafka
```

### Elasticsearch memory hatası veriyor

Docker Desktop veya cluster kaynak limitlerini artırın. Elasticsearch için manifest ve Compose dosyasında `ES_JAVA_OPTS=-Xms512m -Xmx512m` kullanılıyor; yine de sistemde yeterli RAM gerekir.

### Kubernetes image pull hatası

Podlarda `ImagePullBackOff` görürseniz lokal imajlar cluster tarafından görülemiyor olabilir.

Minikube:

```bash
minikube docker-env | Invoke-Expression
docker images | findstr edubase
```

kind:

```bash
kind load docker-image edubase/api-gateway:latest
```

### PostgreSQL init script tekrar çalışmıyor

PostgreSQL init scriptleri sadece boş data dizininde çalışır. Docker Compose'da temiz başlamak için:

```bash
docker compose down -v
docker compose up --build -d
```

Kubernetes'te PVC temizlemek için namespace'i silip tekrar oluşturabilirsiniz:

```bash
kubectl delete namespace edubase
kubectl apply -f k8s/00-namespace.yaml
```

### Recommendation service çalışmasa da öneriler geliyor

Bu beklenen davranıştır. `course-service`, recommendation service'e ulaşamazsa fallback recommendation algoritmasını kullanır. AI servisini aktif kullanmak için `RECOMMENDATION_SERVICE_BASE_URL` değerinin çalışan FastAPI servisini gösterdiğinden emin olun.

## Güvenlik Notları

- README gibi public dokümantasyonlarda gerçek secret, token, app password, webhook secret veya erişim anahtarı bulunmamalıdır.
- `.env` ve Kubernetes Secret dosyaları repository'ye commit edilmemeli; gerekiyorsa yalnızca `.env.example` gibi placeholder içeren örnek dosyalar tutulmalıdır.
- Production ortamında Kubernetes Secret veya harici secret manager kullanın.
- `JWT_SECRET` yeterince uzun ve tahmin edilemez olmalıdır.
- MinIO ve DB şifreleri ortam bazlı değiştirilmelidir.
- Gmail app password gibi hassas bilgiler public repository'ye konulmamalıdır.
- NodePort servisleri production için Ingress, TLS ve network policy ile sertleştirilmelidir.
