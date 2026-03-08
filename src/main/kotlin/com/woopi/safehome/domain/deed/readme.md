# deed 도메인

등기부등본(PDF) 분석 요청을 받아 처리하는 핵심 도메인.
실제 분석 Job의 상태 관리 및 비동기 처리는 `analysisjob` 도메인에 위임한다.

---

## 패키지 구조

```
deed/
├── adapter/
│   ├── inbound/web/
│   │   ├── DeedInboundWebAdapter     # REST 컨트롤러
│   │   └── dto/
│   │       ├── DeedRequest           # 요청 DTO
│   │       ├── DeedResponse          # 응답 DTO
│   │       └── DeedDtoMapper         # DTO <-> UseCase 파라미터 변환
│   └── outbound/
│       └── DeedPdfAnalysisAdapter    # analysisjob의 PdfAnalysisPort 구현체
│
├── application/
│   ├── port/
│   │   ├── inbound/
│   │   │   └── DeedUseCase           # 등기부 분석 요청 인터페이스
│   │   └── outbound/                 # (analysisjob 포트를 직접 사용)
│   └── usecase/
│       └── DeedUseCaseImpl           # Job 생성 -> SSE 연결 -> 비동기 실행 트리거
│
└── domain/
    ├── model/
    │   └── DeedSections              # 등기부등본 섹션 모델 (표제부/갑구/을구)
    └── service/
        ├── PdfParserService          # PDF 파싱 인터페이스
        ├── PdfValidationService      # PDF 유효성 검증 인터페이스
        ├── exception/
        │   └── InvalidPdfException   # 유효하지 않은 PDF 예외
        └── impl/
            ├── DefaultPdfParserService
            └── DefaultPdfValidationService
```

---

## analysisjob과의 의존 관계

deed는 analysisjob에 단방향으로 의존한다.
analysisjob은 deed를 직접 참조하지 않으며, `PdfAnalysisPort`를 통해 deed의 PDF 처리 능력을 사용한다.

**deed → analysisjob (사용)**

| 포트 | 용도 |
|------|------|
| `AnalysisJobPersistencePort` | 분석 Job 생성 및 저장 |
| `AnalysisSseNotifierPort` | SSE Emitter 발급 및 초기 이벤트 전송 |
| `AnalysisJobExecutorPort` | 비동기 분석 실행 트리거 |

**analysisjob → deed (구현 제공)**

| 포트 | 구현체 | 용도 |
|------|--------|------|
| `PdfAnalysisPort` | `DeedPdfAnalysisAdapter` | PDF 유효성 검증 + 파싱 |
