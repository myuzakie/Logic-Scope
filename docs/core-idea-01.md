# PRD — LogicScope MVP

## 1. Ringkasan Produk

LogicScope adalah local-first debugging platform yang membantu developer memahami mengapa sebuah request menghasilkan business outcome tertentu.

LogicScope bekerja terhadap repository aplikasi yang sudah ada. Developer cukup memberikan URL repository atau path project lokal. LogicScope kemudian:

```text
Clone / load repository
        ↓
Detect project capabilities
        ↓
Run application locally
        ↓
Execute HTTP request
        ↓
Collect runtime trace
        ↓
Identify executed source path
        ↓
Collect runtime evidence
        ↓
Analyze business logic
        ↓
Display causal explanation
```

LogicScope bukan generic code chatbot, bukan production APM, dan bukan automatic code repair tool.

---

# 2. Problem Statement

Pada aplikasi Java/Spring Boot yang besar, developer sering mengetahui hasil akhirnya, tetapi sulit menjawab:

- Request masuk melalui endpoint mana?
- Service apa saja yang benar-benar dipanggil?
- Class dan method mana yang dieksekusi?
- Rule bisnis mana yang menyebabkan hasil akhir?
- Mengapa sistem mengembalikan status tertentu walaupun semua HTTP request sukses?
- Apakah kesimpulan tersebut benar-benar didukung oleh runtime evidence?

Contoh masalah:

```text
HTTP request berhasil 200 OK
Tetapi business decision:
REJECT_LIMIT
```

Developer membutuhkan penjelasan:

```text
requestedAmount = 25,000,000
        ↓
DSR = 41%
        ↓
MAX_DSR = 35%
        ↓
availablePayment = 0
        ↓
approvedLimit = 1,800,000
        ↓
approvedLimit < requestedAmount
        ↓
REJECT_LIMIT
```

Tanpa LogicScope, developer harus membaca banyak file source secara manual, menelusuri call chain, menjalankan debugger, dan menebak hubungan antara source code dan output runtime.

---

# 3. Product Vision

LogicScope menjadi alat yang mampu menjawab:

> “Untuk request ini, source code mana yang benar-benar berjalan, keputusan bisnis apa yang dibuat, dan bukti runtime apa yang menyebabkan hasil tersebut?”

Output utama LogicScope bukan sekadar daftar file, tetapi causal explanation yang dapat diverifikasi.

---

# 4. Target User

## Primary user

Java/Spring Boot developer yang:

- bekerja pada repository besar;
- menerima bug atau laporan business outcome yang salah;
- membutuhkan pemahaman cepat terhadap execution flow;
- menjalankan project secara lokal;
- memiliki akses terhadap source code dan konfigurasi project.

## Secondary user

- technical lead;
- backend engineer;
- QA engineer;
- SRE yang perlu memahami business failure;
- developer yang melakukan incident investigation.

---

# 5. Product Scope

## In scope untuk MVP

LogicScope MVP harus mendukung:

- repository Java;
- Java 17 atau Java 21;
- Maven;
- Spring Boot 3.x;
- Spring MVC;
- synchronous HTTP execution;
- local execution;
- OpenTelemetry Java Agent;
- PostgreSQL atau SQLite untuk development;
- source mapping berbasis convention;
- semantic explanation berbasis source dan runtime data.

## Out of scope untuk MVP

Fitur berikut tidak termasuk MVP:

- Gradle;
- Node.js, Python, .NET;
- Kubernetes execution;
- cloud execution;
- Kafka;
- WebFlux;
- universal Java call graph;
- production APM;
- automatic code repair;
- semantic breakpoint;
- replay penuh;
- counterfactual debugging;
- IDE extension;
- vector database;
- multi-tenant SaaS;
- autonomous agent yang mengubah source code.

---

# 6. Prinsip Produk

## 6.1 Runtime first

LogicScope harus memulai analisis dari request runtime nyata, bukan hanya static source scanning.

## 6.2 Evidence-based explanation

AI hanya boleh menggunakan data yang diberikan oleh source code, trace, dan runtime evidence.

Jika bukti tidak cukup, hasil harus menyatakan:

```text
Insufficient evidence
```

bukan membuat asumsi.

## 6.3 Narrow execution scope

LogicScope tidak mengirim seluruh repository ke semantic engine. Hanya source yang relevan dengan execution path yang dikirim.

## 6.4 Local-first

Source code dan runtime data tetap berada di mesin pengguna secara default.

## 6.5 Deterministic investigation

Investigation yang sama dengan input dan repository yang sama harus menghasilkan output yang konsisten atau setidaknya dapat dijelaskan perbedaannya.

## 6.6 Convention over universal analysis

MVP hanya mendukung pola Java/Spring yang umum:

- `@RestController`;
- `@Controller`;
- `@Service`;
- `@Component`;
- Spring MVC;
- Maven;
- OpenTelemetry Java Agent.

---

# 7. User Journey

## 7.1 Load project

Developer menjalankan:

```bash
./logicscope init https://github.com/example/loan-service.git
```

LogicScope:

1. melakukan clone repository;
2. membuat workspace project;
3. membaca struktur project;
4. mendeteksi Maven dan Spring Boot;
5. menghasilkan capability report.

## 7.2 Configure runtime

Developer mengatur:

```yaml
project:
  root: .

runtime:
  type: local
  command: ./mvnw spring-boot:run
  port: 8080
  healthPath: /actuator/health

investigation:
  request:
    method: POST
    path: /applications
    headers:
      Content-Type: application/json
    body: |
      {
        "customerId": "CUS-001",
        "requestedAmount": 25000000
      }
```

## 7.3 Start application

Developer menjalankan:

```bash
./logicscope run loan-service
```

LogicScope:

1. membaca konfigurasi;
2. menjalankan application;
3. memasang OpenTelemetry Java Agent;
4. menunggu port aktif;
5. melakukan health check;
6. menampilkan status application.

## 7.4 Run investigation

Developer menjalankan:

```bash
./logicscope investigate loan-service
```

LogicScope:

1. menjalankan HTTP request;
2. mengambil response;
3. menunggu trace selesai;
4. menyimpan trace;
5. menemukan source path;
6. mengumpulkan evidence;
7. membuat causal explanation;
8. menampilkan hasil melalui API atau dashboard.

## 7.5 Read result

Contoh hasil:

```text
Outcome: REJECT_LIMIT

Causal chain:

1. DecisionService.evaluate()
2. existingInstallment / verifiedIncome = 41%
3. MAX_DSR threshold = 35%
4. MAX_DSR condition evaluated as FAIL
5. availablePayment set to 0
6. approvedLimit calculated as 1,800,000
7. requestedAmount = 25,000,000
8. approvedLimit < requestedAmount
9. final decision = REJECT_LIMIT
```

---

# 8. Functional Requirements

## FR-001 — Project registration

System harus dapat menerima:

- local path;
- Git repository URL.

System harus membuat project record:

```json
{
  "projectId": "proj-123",
  "name": "loan-service",
  "sourceType": "GIT",
  "sourceUrl": "https://github.com/example/loan-service.git",
  "localPath": "...",
  "status": "READY"
}
```

## FR-002 — Repository cloning

System harus:

- clone repository ke workspace LogicScope;
- menyimpan commit SHA;
- mencegah clone ulang yang tidak diperlukan;
- melaporkan error clone secara jelas;
- tidak mengubah repository asli pengguna.

MVP dapat membatasi clone pada repository yang dapat diakses melalui Git tanpa authentication kompleks.

## FR-003 — Capability discovery

System harus mendeteksi minimal:

- Java;
- Java version;
- Maven;
- Maven multi-module;
- Spring Boot;
- Spring MVC;
- Spring DI;
- OpenTelemetry configuration jika tersedia.

Capability status:

```text
SUPPORTED
SUPPORTED_WITH_LIMITATIONS
UNSUPPORTED
UNKNOWN
```

## FR-004 — Runtime configuration

System harus mendukung konfigurasi:

- command untuk menjalankan aplikasi;
- working directory;
- port;
- health endpoint;
- environment variables;
- startup timeout;
- request timeout;
- HTTP request investigation.

## FR-005 — Application lifecycle

System harus dapat:

- start application;
- mendeteksi application ready;
- melakukan health check;
- menyimpan process information;
- stop application;
- mengidentifikasi startup failure;
- menghindari orphan process.

Contoh status:

```text
CREATED
STARTING
RUNNING
FAILED
STOPPING
STOPPED
```

## FR-006 — HTTP request execution

System harus dapat menjalankan request:

- GET;
- POST;
- PUT;
- PATCH;
- DELETE.

Input request minimal:

```json
{
  "method": "POST",
  "url": "http://localhost:8080/applications",
  "headers": {},
  "body": {}
}
```

Output:

```json
{
  "statusCode": 200,
  "headers": {},
  "body": {},
  "durationMs": 245
}
```

## FR-007 — Distributed tracing

LogicScope harus mengaktifkan OpenTelemetry Java Agent pada target project.

System harus mengumpulkan:

- trace ID;
- span ID;
- parent span ID;
- service name;
- operation name;
- HTTP method;
- HTTP route;
- status code;
- duration;
- timestamp;
- error status jika ada.

## FR-008 — Trace storage

Trace harus disimpan dalam format internal LogicScope.

Model minimal:

```text
Investigation
  └── Trace
        └── Span
              └── Attributes
```

System harus dapat mengambil trace berdasarkan:

- investigation ID;
- trace ID;
- project ID.

## FR-009 — Execution path generation

System harus mengubah trace menjadi execution path:

```json
{
  "services": [
    {
      "name": "loan-service",
      "route": "POST /applications"
    }
  ],
  "sourcePath": [
    "ApplicationController.submit",
    "ApplicationService.process",
    "DecisionService.evaluate"
  ]
}
```

Pada MVP, source mapping dapat menggunakan:

- endpoint route;
- controller annotation;
- class name;
- method name;
- trace metadata;
- lexical source search;
- Spring conventions.

## FR-010 — Source slicing

System harus memilih source yang relevan berdasarkan:

- class yang berhubungan dengan route;
- service yang dipanggil;
- method yang ditemukan;
- dependencies lokal yang relevan;
- source file yang berisi branch atau return decision.

System tidak boleh mengirim seluruh repository ke AI secara default.

## FR-011 — Runtime evidence

System harus mengumpulkan evidence minimum dari:

- HTTP request;
- HTTP response;
- trace attributes;
- span status;
- method return value bila tersedia;
- branch condition bila instrumentasi mendukung;
- selected variable values bila instrumentasi mendukung.

Setiap evidence harus memiliki provenance:

```json
{
  "type": "SOURCE",
  "value": "approvedLimit < requestedAmount",
  "sourceFile": "DecisionService.java",
  "line": 61,
  "confidence": "HIGH"
}
```

## FR-012 — Causal analysis

Semantic engine harus menghasilkan:

- final outcome;
- causal steps;
- source location;
- runtime evidence;
- confidence;
- unsupported or unknown claims.

Contoh:

```json
{
  "outcome": "REJECT_LIMIT",
  "confidence": "HIGH",
  "causalChain": [
    {
      "step": 1,
      "rule": "MAX_DSR",
      "condition": "41% > 35%",
      "result": "FAIL",
      "source": {
        "file": "DecisionService.java",
        "line": 42
      },
      "evidence": {
        "existingInstallment": 2500000,
        "verifiedIncome": 8000000,
        "maxDsr": 0.35
      }
    }
  ]
}
```

## FR-013 — Investigation state

Investigation harus memiliki lifecycle:

```text
CREATED
PROJECT_VALIDATION
APPLICATION_STARTING
APPLICATION_READY
REQUEST_EXECUTED
TRACE_COLLECTED
SOURCE_MAPPED
EVIDENCE_COLLECTED
ANALYSIS_COMPLETED
FAILED
```

## FR-014 — Dashboard

Dashboard minimal harus menampilkan:

- project;
- capability report;
- runtime status;
- investigation history;
- request;
- response;
- trace tree;
- execution path;
- source snippets;
- causal chain;
- evidence;
- confidence.

---

# 9. Non-Functional Requirements

## NFR-001 — Local-first security

- Source code tidak dikirim keluar tanpa konfigurasi eksplisit.
- API key AI tidak boleh ditulis ke repository.
- Secret harus dibaca dari environment variable atau secure configuration.
- Log tidak boleh mencetak token atau password.
- Repository path harus divalidasi untuk mencegah path traversal.

## NFR-002 — Reproducibility

Investigation harus menyimpan:

- project commit SHA;
- runtime configuration;
- request;
- application version;
- LogicScope version;
- trace ID;
- analysis result.

## NFR-003 — Failure transparency

Jika tahap tertentu gagal, system harus menunjukkan:

```text
Stage: TRACE_COLLECTION
Status: FAILED
Reason: OpenTelemetry exporter did not return any span
Suggested action: Verify OTLP endpoint and application startup logs
```

## NFR-004 — Performance

Target MVP:

- repository discovery selesai dalam waktu wajar untuk repository medium;
- application startup timeout dapat dikonfigurasi;
- trace retrieval tidak menggantung tanpa timeout;
- investigation memiliki cancellation mechanism.

## NFR-005 — Extensibility

Arsitektur harus memungkinkan penambahan:

- Gradle;
- WebFlux;
- Kafka;
- additional trace backends;
- additional semantic providers;
- IDE integrations.

Tetapi extension tersebut tidak perlu diimplementasikan dalam MVP.

---

# 10. Arsitektur Teknis

Project tetap menggunakan modular monolith:

```text
logicscope-app
  ├── logicscope-domain
  ├── logicscope-discovery
  ├── logicscope-project
  ├── logicscope-runtime
  ├── logicscope-trace
  ├── logicscope-source
  ├── logicscope-evidence
  ├── logicscope-semantic
  ├── logicscope-persistence
  └── logicscope-replay
```

## Modul yang perlu ditambahkan

### `logicscope-project`

Tanggung jawab:

- project registration;
- Git clone;
- workspace management;
- commit tracking;
- project configuration.

### `logicscope-runtime`

Tanggung jawab:

- process start/stop;
- health check;
- port management;
- environment injection;
- startup logs;
- process cleanup.

### `logicscope-trace`

Tanggung jawab:

- OTLP receiver;
- trace ingestion;
- span normalization;
- trace tree;
- trace persistence;
- trace filtering.

### `logicscope-source`

Tanggung jawab:

- endpoint discovery;
- controller mapping;
- source slicing;
- method and class matching;
- source location extraction.

### `logicscope-evidence`

Tanggung jawab:

- evidence model;
- runtime evidence capture;
- provenance;
- confidence;
- evidence validation.

### `logicscope-semantic`

Tanggung jawab:

- prompt construction;
- source and evidence packaging;
- causal chain result;
- confidence;
- unsupported claim detection.

---

# 11. Proposed Data Model

## Project

```text
id
name
source_type
source_url
local_path
commit_sha
status
created_at
updated_at
```

## ProjectConfiguration

```text
project_id
runtime_command
working_directory
port
health_path
environment
startup_timeout
request_timeout
```

## Investigation

```text
id
project_id
status
request_method
request_path
request_headers
request_body
response_status
response_body
trace_id
started_at
completed_at
failure_reason
```

## Span

```text
id
investigation_id
trace_id
span_id
parent_span_id
service_name
operation_name
http_method
http_route
status_code
start_time
duration
attributes
```

## SourceMatch

```text
id
investigation_id
file_path
class_name
method_name
line_start
line_end
match_type
confidence
```

## RuntimeEvidence

```text
id
investigation_id
type
key
value
source
source_file
source_line
confidence
```

## CausalStep

```text
id
investigation_id
sequence
rule_name
condition
result
explanation
source_file
source_line
evidence_ids
confidence
```

---

# 12. API Design

## Register project

```http
POST /api/projects
```

Request:

```json
{
  "sourceType": "GIT",
  "sourceUrl": "https://github.com/example/project.git",
  "name": "example-project"
}
```

## Inspect project

```http
POST /api/projects/{projectId}/inspect
```

Response:

```json
{
  "projectId": "proj-123",
  "capabilities": [
    {
      "name": "JAVA",
      "status": "SUPPORTED"
    },
    {
      "name": "MAVEN",
      "status": "SUPPORTED"
    },
    {
      "name": "SPRING_BOOT",
      "status": "SUPPORTED"
    }
  ]
}
```

## Start project

```http
POST /api/projects/{projectId}/runtime/start
```

## Stop project

```http
POST /api/projects/{projectId}/runtime/stop
```

## Create investigation

```http
POST /api/investigations
```

Request:

```json
{
  "projectId": "proj-123",
  "method": "POST",
  "path": "/applications",
  "headers": {
    "Content-Type": "application/json"
  },
  "body": {
    "customerId": "CUS-001",
    "requestedAmount": 25000000
  }
}
```

## Get investigation

```http
GET /api/investigations/{investigationId}
```

## Get causal chain

```http
GET /api/investigations/{investigationId}/causal-chain
```

---

# 13. CLI Design

```bash
./logicscope init <repository-url-or-path>
./logicscope inspect <project>
./logicscope configure <project>
./logicscope start <project>
./logicscope investigate <project>
./logicscope trace <investigation-id>
./logicscope explain <investigation-id>
./logicscope stop <project>
```

Contoh alur:

```bash
./logicscope init https://github.com/example/project.git
./logicscope inspect project
./logicscope configure project
./logicscope start project
./logicscope investigate project
./logicscope explain investigation-id
```

---

# 14. MVP Milestones

## Milestone 1 — Project management

Target:

- clone repository;
- register local project;
- buat `.logicscope/config.yaml`;
- detect Java/Maven/Spring Boot;
- tampilkan capability report.

Acceptance criteria:

- project dapat di-register dari URL;
- commit SHA tersimpan;
- project dapat ditemukan kembali;
- unsupported project ditolak dengan alasan jelas.

## Milestone 2 — Runtime execution

Target:

- menjalankan target Spring Boot;
- health check;
- stop process;
- request execution.

Acceptance criteria:

- LogicScope dapat memulai target project;
- LogicScope mendeteksi readiness;
- HTTP request dapat dijalankan;
- application failure dapat ditampilkan.

## Milestone 3 — Real tracing

Target:

- OpenTelemetry Java Agent;
- OTLP ingestion;
- trace persistence;
- trace tree.

Acceptance criteria:

- satu request memiliki trace ID;
- trace memiliki span hierarchy;
- HTTP route dan service name terbaca;
- trace dapat dilihat melalui API.

## Milestone 4 — Source mapping

Target:

- route ke controller;
- controller ke service;
- source location;
- execution slice.

Acceptance criteria:

- system menemukan source controller;
- system menemukan service yang relevan;
- source tidak diambil dari seluruh repository;
- hasil mapping memiliki confidence.

## Milestone 5 — Runtime evidence

Target:

- input/output evidence;
- branch evidence;
- variable evidence terbatas.

Acceptance criteria:

- evidence memiliki provenance;
- evidence dapat dikaitkan dengan causal step;
- evidence yang tidak tersedia ditandai `UNKNOWN`.

## Milestone 6 — Causal analysis

Target:

- semantic analysis;
- causal chain;
- source references;
- confidence.

Acceptance criteria:

- output menjelaskan business outcome;
- setiap langkah memiliki source atau evidence;
- AI tidak membuat klaim tanpa bukti;
- hasil dapat ditampilkan sebagai graph.

## Milestone 7 — MVP demo flow

Target end-to-end:

```text
Repository URL
    ↓
Clone
    ↓
Inspect
    ↓
Run
    ↓
HTTP request
    ↓
Trace
    ↓
Execution slice
    ↓
Evidence
    ↓
Causal explanation
```

Acceptance criteria utama:

> Developer dapat memberikan satu repository Spring Boot, menjalankan satu request, lalu memperoleh penjelasan causal terhadap response berdasarkan trace, source code, dan runtime evidence.

---

# 15. Test Strategy

Demo application tidak perlu menjadi fitur produk, tetapi project tetap membutuhkan test fixture internal.

Fixture digunakan untuk:

- integration test;
- OpenTelemetry test;
- source mapping test;
- causal analysis test;
- regression test.

Struktur:

```text
.e2e-test-project/
  ├── pom.xml
  ├── src/main/java/
  ├── .logicscope/config.yaml
  └── test-data/
```

Test end-to-end:

```text
Given repository fixture
When LogicScope clones or loads it
And starts the application
And executes configured request
Then HTTP response is available
And trace is collected
And source path is discovered
And causal result is generated
```

Fixture bukan bagian dari user-facing product. Fixture adalah alat validasi internal.

---

# 16. Definition of Done MVP

MVP dianggap selesai jika semua kondisi berikut terpenuhi:

- repository Java/Spring Boot dapat di-load;
- project capability dapat dideteksi;
- application dapat dijalankan lokal;
- HTTP request dapat dikonfigurasi;
- request dapat dieksekusi;
- OpenTelemetry trace dapat dikumpulkan;
- trace dapat disimpan;
- endpoint dapat dipetakan ke source;
- execution slice dapat dihasilkan;
- source relevan dapat ditampilkan;
- runtime evidence dapat ditampilkan;
- causal explanation dapat dihasilkan;
- setiap kesimpulan memiliki evidence atau source reference;
- kegagalan setiap tahap dapat dijelaskan;
- seluruh flow berjalan melalui CLI atau dashboard;
- automated end-to-end test tersedia.

---

# 17. Prioritas Implementasi Saat Ini

Berdasarkan kondisi repository sekarang, urutan coding paling tepat adalah:

```text
1. Tambahkan project lifecycle module
2. Perbaiki init agar mendukung repository URL
3. Tambahkan runtime process manager
4. Tambahkan request execution
5. Implementasikan logicscope-trace
6. Tambahkan OTLP receiver
7. Simpan trace dan span
8. Implementasikan controller/source mapping
9. Buat execution slice
10. Implementasikan runtime evidence
11. Implementasikan semantic causal analysis
12. Tambahkan investigation dashboard
13. Buat end-to-end fixture test
```

Jadi project tidak perlu dimulai dari pembuatan `loan-service`, `credit-service`, dan `decision-service` sebagai bagian utama. Project harus dibangun sebagai platform yang menerima repository eksternal.

Akan tetapi, satu repository kecil tetap diperlukan secara internal untuk menguji seluruh pipeline secara otomatis.