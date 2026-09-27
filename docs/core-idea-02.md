Spring Petclinic sangat cocok sebagai repository pertama untuk demo LogicScope, tetapi dengan satu batasan penting:

> Spring Petclinic cocok untuk membuktikan repository discovery, runtime execution, tracing, source mapping, dan business-flow explanation. Namun, aplikasi ini bukan contoh terbaik untuk membuktikan distributed tracing antar-microservice karena versi canonical-nya adalah satu aplikasi Spring Boot monolith.

Repository resminya berbasis Spring Boot, Spring MVC, Spring Data JPA, Maven, dan membutuhkan Java 17 atau lebih baru. Aplikasi dapat dijalankan dengan `./mvnw spring-boot:run` dan secara default berjalan di port `8080`. Database default-nya menggunakan H2 in-memory sehingga tidak memerlukan setup database eksternal untuk demo dasar. 

## Posisi Spring Petclinic dalam MVP LogicScope

Alurnya menjadi:

```text
Spring Petclinic repository
        ↓
LogicScope clone/load repository
        ↓
Capability discovery
        ↓
Start Spring Boot application
        ↓
Execute HTTP request
        ↓
Collect OpenTelemetry trace
        ↓
Map trace ke Controller/Service/Repository
        ↓
Collect source and runtime evidence
        ↓
Generate explanation
```

Contoh flow yang bisa dianalisis:

```text
POST /owners/new
        ↓
OwnerController.processCreationForm()
        ↓
OwnerRepository.save()
        ↓
H2 database
        ↓
Redirect ke /owners/{id}
```

Atau:

```text
GET /owners/{ownerId}
        ↓
OwnerController.showOwner()
        ↓
OwnerRepository.findById()
        ↓
Owner detail page
```

Untuk API-based investigation, repository `spring-petclinic-rest` lebih cocok karena menyediakan endpoint REST untuk owners, pets, visits, veterinarians, dan specialties. Repository tersebut juga menyediakan Swagger UI dan OpenAPI documentation. 

## Rekomendasi repository yang digunakan

### Pilihan pertama: canonical Spring Petclinic

Repository:

```text
https://github.com/spring-projects/spring-petclinic.git
```

Kelebihan:

- resmi dan dikenal luas;
- stabil untuk demo;
- Spring Boot;
- Spring MVC;
- Spring Data JPA;
- Maven;
- tersedia data awal;
- dapat dijalankan tanpa PostgreSQL;
- memiliki struktur Controller → Service → Repository.

Kekurangan:

- monolith;
- sebagian besar behavior berupa CRUD;
- belum memiliki business decision yang kompleks;
- tidak menunjukkan komunikasi antar-service.

Repository ini cocok untuk tahap:

```text
clone → inspect → run → request → trace → source mapping
```

### Pilihan kedua: Spring Petclinic REST

Repository:

```text
https://github.com/spring-petclinic/spring-petclinic-rest.git
```

Kelebihan:

- memiliki REST API;
- mudah dipanggil dari LogicScope;
- cocok untuk request-based investigation;
- endpoint terdokumentasi;
- lebih mudah digunakan dalam automated test.

Kekurangan:

- business rules tetap sederhana;
- tidak secara langsung menunjukkan causal decision yang kompleks;
- tidak otomatis menghasilkan distributed trace antar-service.

Untuk MVP LogicScope, saya lebih menyarankan `spring-petclinic-rest` jika fokus utama adalah menjalankan request dan menampilkan hasil melalui API. Jika ingin menunjukkan kemampuan membaca aplikasi Spring MVC tradisional dan HTML flow, gunakan canonical `spring-petclinic`.

## Apakah Petclinic sudah cukup untuk business causal analysis?

Cukup untuk tahap pertama, tetapi belum cukup untuk membuktikan seluruh visi LogicScope.

Petclinic memiliki business flow seperti:

```text
Owner creation
Pet creation
Visit creation
Owner search
Veterinarian lookup
Specialty assignment
```

Namun sebagian besar keputusan bisnisnya sederhana. Contoh causal explanation yang realistis:

```text
Request:
POST /owners/new

Execution:
OwnerController.processCreationForm()
    ↓
OwnerValidator.validate()
    ↓
OwnerRepository.save()

Result:
Owner berhasil dibuat

Reason:
- firstName tidak kosong
- lastName tidak kosong
- address valid
- city valid
- telephone valid
- tidak ada validation error
- entity disimpan melalui repository
```

Contoh failure:

```text
Request:
POST /owners/new

Input:
telephone = "abc"

Execution:
OwnerValidator.validate()
    ↓
Errors.rejectValue("telephone", "typeMismatch")

Result:
Form dikembalikan dengan validation error

Causal explanation:
Telephone harus berupa angka, sehingga validasi gagal sebelum data disimpan.
```

Flow ini sudah cocok untuk membuktikan:

- source mapping;
- branch analysis;
- validation evidence;
- method execution;
- business outcome explanation.

Namun belum cocok untuk demo seperti:

```text
41% > 35%
    ↓
LIMIT_FAIL
    ↓
REJECT_LIMIT
```

Jika ingin causal business decision yang lebih kuat, sebaiknya tambahkan scenario bisnis kecil ke cloned Petclinic sebagai demo branch atau gunakan repository kedua yang memiliki rule lebih kompleks.

## Cara menggunakan Petclinic tanpa mengubah tujuan produk

Petclinic jangan diperlakukan sebagai aplikasi demo yang harus dimasukkan ke source code LogicScope. Petclinic diperlakukan sebagai target repository eksternal.

Struktur workspace:

```text
logicScope/
├── backend/
├── cli/
├── ui/
├── scripts/
└── projects/
    └── spring-petclinic/
```

Atau lebih baik:

```text
logicScope/
├── backend/
├── cli/
├── ui/
└── .logicscope-workspace/
    └── projects/
        └── spring-petclinic/
```

LogicScope sendiri tidak boleh bergantung pada package atau class khusus Petclinic.

## Konfigurasi Petclinic

Contoh konfigurasi:

```yaml
project:
  name: spring-petclinic
  root: .
  source:
    type: git
    url: https://github.com/spring-projects/spring-petclinic.git
    commit: main

runtime:
  type: local
  command: ./mvnw spring-boot:run
  workingDirectory: .
  port: 8080
  healthPath: /

observability:
  enabled: true
  protocol: otlp
  endpoint: http://localhost:4317

investigation:
  request:
    method: GET
    path: /owners/1
    headers: {}
```

Untuk repository canonical, command yang direkomendasikan adalah:

```bash
./mvnw spring-boot:run
```

Aplikasi default berjalan di:

```text
http://localhost:8080
```

Sementara database default menggunakan H2 in-memory dan data awal di-load saat startup. 

## Demo flow yang direkomendasikan

### Demo 1 — Repository discovery

User menjalankan:

```bash
./logicscope init https://github.com/spring-projects/spring-petclinic.git
```

LogicScope menghasilkan:

```text
Project: spring-petclinic
Language: Java
Java version: 17+
Build system: Maven
Framework: Spring Boot
Web framework: Spring MVC
Persistence: Spring Data JPA
Runtime support: Supported
```

### Demo 2 — Application startup

```bash
./logicscope start spring-petclinic
```

Output:

```text
Project loaded
Build system detected: Maven
Starting application
Waiting for port 8080
Application is ready
Runtime URL: http://localhost:8080
```

### Demo 3 — Source mapping

User menjalankan:

```bash
./logicscope investigate spring-petclinic \
  --method GET \
  --path /owners/1
```

LogicScope menampilkan:

```text
HTTP GET /owners/1

Mapped execution:

OwnerController.showOwner()
    ↓
OwnerRepository.findById()
    ↓
Owner entity loaded
    ↓
Owner detail rendered
```

### Demo 4 — Validation failure

User menjalankan request pembuatan owner dengan input tidak valid:

```text
POST /owners/new
telephone = "abc"
```

Output:

```text
Outcome: VALIDATION_ERROR

Causal chain:

1. OwnerController.processCreationForm()
2. OwnerValidator.validate()
3. telephone failed numeric validation
4. bindingResult.hasErrors() = true
5. owner creation was not persisted
6. form page returned to user
```

Ini adalah demo yang lebih baik daripada hanya menampilkan trace sukses karena LogicScope memang dirancang untuk menjelaskan mengapa outcome tertentu terjadi.

### Demo 5 — Repository persistence

Request:

```text
POST /owners/new
```

Dengan input valid:

```text
firstName = John
lastName = Doe
address = Jakarta
city = Jakarta
telephone = 08123456789
```

Output:

```text
Outcome: OWNER_CREATED

Causal chain:

1. Request matched OwnerController
2. OwnerValidator returned no validation errors
3. OwnerService accepted owner
4. OwnerRepository.save() executed
5. H2 transaction completed
6. Application redirected to owner detail page
```

## Keterbatasan tracing pada Petclinic

Karena canonical Petclinic adalah monolith, trace yang dihasilkan kemungkinan terlihat seperti:

```text
HTTP GET /owners/1
    ↓
Spring MVC controller
    ↓
Spring service or repository
    ↓
H2 database
```

Bukan:

```text
service-a
    ↓
service-b
    ↓
service-c
```

Jadi istilah “distributed tracing” pada demo Petclinic perlu dijelaskan sebagai:

> distributed tracing capability yang sedang diuji pada satu aplikasi, bukan distributed microservice topology.

Untuk menguji nested service spans, cukup gunakan controller, service, dan repository span dalam satu process. Untuk menguji benar-benar antar-service, gunakan repository lain seperti Petclinic microservices atau buat fixture internal kecil. Versi microservices Petclinic memang tersedia secara terpisah dalam komunitas Spring Petclinic. 

## Rekomendasi implementasi untuk kondisi sekarang

Dengan Petclinic sebagai target pertama, backlog LogicScope sebaiknya diubah menjadi:

### Phase 1 — Petclinic compatibility

- clone canonical Petclinic;
- deteksi Maven;
- deteksi Java;
- deteksi Spring Boot;
- deteksi Spring MVC;
- deteksi Spring Data JPA;
- jalankan `./mvnw spring-boot:run`;
- health check port 8080;
- execute GET `/owners/1`.

### Phase 2 — Trace collection

- pasang OpenTelemetry Java Agent;
- buat OTLP receiver;
- ambil trace GET `/owners/1`;
- simpan span;
- tampilkan trace tree.

### Phase 3 — Controller mapping

- map `/owners/{ownerId}` ke `OwnerController`;
- temukan method `showOwner`;
- ambil source file dan line number;
- tampilkan source snippet.

### Phase 4 — Service/repository mapping

- identifikasi service call;
- identifikasi repository call;
- buat execution path;
- hitung confidence mapping.

### Phase 5 — Business outcome

- capture response status;
- capture page/redirect outcome;
- detect validation branch;
- tampilkan alasan business outcome.

### Phase 6 — Semantic explanation

- kirim execution slice ke semantic module;
- hasilkan causal chain;
- tampilkan source reference dan evidence;
- tandai bagian yang belum memiliki runtime evidence.

## Acceptance criteria khusus Petclinic

MVP dianggap berhasil jika LogicScope dapat melakukan hal berikut terhadap Petclinic:

```text
1. Load spring-petclinic dari repository publik
2. Detect Java, Maven, Spring Boot, Spring MVC, dan JPA
3. Start aplikasi secara lokal
4. Detect aplikasi ready di port 8080
5. Execute GET /owners/1
6. Collect trace ID dan span
7. Map request ke OwnerController
8. Map execution ke repository/database layer
9. Tampilkan source snippet yang relevan
10. Tampilkan response dan duration
11. Menjelaskan outcome request
12. Menjalankan request invalid
13. Menjelaskan validation failure berdasarkan source dan runtime evidence
```

## Kesimpulan

Spring Petclinic adalah pilihan yang baik untuk demo pertama karena:

- public;
- resmi;
- mudah dijalankan;
- menggunakan stack yang menjadi target LogicScope;
- memiliki Controller, Service, Repository, validation, dan database;
- tidak membutuhkan infrastruktur berat.

Tetapi Petclinic sebaiknya diposisikan sebagai:

```text
reference target untuk menguji LogicScope
```

bukan sebagai:

```text
business scenario utama yang ditanam ke dalam LogicScope
```

Untuk demo MVP paling kuat, gunakan dua skenario:

```text
Skenario sukses:
GET /owners/1
→ map request ke source dan tampilkan execution path

Skenario gagal:
POST /owners/new dengan telephone invalid
→ jelaskan validation branch dan alasan data tidak disimpan
```

Dengan demikian, LogicScope sudah dapat membuktikan inti produk:

```text
repository nyata
    → request nyata
    → trace nyata
    → source path nyata
    → business explanation berbasis evidence
```