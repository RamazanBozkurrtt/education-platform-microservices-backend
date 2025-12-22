# Edubase Microservices Backend

Bu proje, Spring Boot ve Mikroservis mimarisi kullanılarak geliştirilmiş, ölçeklenebilir bir online eğitim platformu (Udemy Klonu) altyapısıdır.

## Mimari Yapı

Proje **Multi-Module Maven** yapısına sahiptir:

* **api-gateway:** Spring Cloud Gateway ile merkezi giriş noktası.
* **auth-service:** (PostgreSQL) Kimlik doğrulama ve yetkilendirme (JWT).
* **course-service:** (MongoDB) Kurs içerik yönetimi (NoSQL).
* **order-service:** (PostgreSQL) Sipariş ve ödeme işlemleri (TSID).
* **common:** Tüm servislerin kullandığı ortak kütüphaneler (DTOs, Exceptions).

## Teknoloji Stack'i

* **Dil:** Java 21
* **Framework:** Spring Boot 3.2.x, Spring Cloud 2023
* **Veritabanı:** PostgreSQL, MongoDB, Redis
* **Messaging:** RabbitMQ
* **Build Tool:** Maven

## Kurulum

(Buraya ileride Docker Compose komutlarını ekleyeceğiz)
