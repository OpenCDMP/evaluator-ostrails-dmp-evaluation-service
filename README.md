# Evaluator OSTrails DMP Evaluation Service for OpenCDMP

**evaluator-ostrails-dmp-evaluation-service** is an implementation of the [evaluator-base](https://github.com/OpenCDMP/evaluator-base) package that evaluates **RDA JSON exports** of Data Management Plans (DMPs) by delegating to the external **[OSTrails DMP Evaluation Service](https://github.com/OSTrails/DMP-Evaluation-Service)**.

## Overview

This service acts as an OpenCDMP evaluator plugin. When a plan evaluation is requested, it forwards the RDA JSON export to the [OSTrails DMP Evaluation Service](https://ostrails-dmp-evaluation.arisnet.ac.at/swagger-ui/index.html#/) — an open-source evaluation engine available at [github.com/OSTrails/DMP-Evaluation-Service](https://github.com/OSTrails/DMP-Evaluation-Service) — and maps the results back into OpenCDMP's standard rank/result format.

Evaluation is benchmark-based: each benchmark represents a set of metrics (e.g. schema compliance, FAIRness, RDM coverage). Benchmarks can be fetched dynamically from the remote OSTrails service or configured locally.

## Features

- **Plan Evaluation**: Evaluates RDA JSON exports against configurable benchmarks via the OSTrails service.
- **Remote Benchmarks**: Fetches available benchmarks directly from the OSTrails API (configurable).
- **Spring Boot Microservice**: Runs as a standalone Spring Boot service with OAuth2/JWT authentication.
- **Audit Logging**: All evaluation requests are tracked via the OpenCDMP audit service.

**Supported operations:**
- ✅ Evaluate and rank plans (RDA JSON export required)
- ❌ Evaluate and rank descriptions (not supported)

---

### API Endpoints

All endpoints are under `/api/evaluator`:

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/evaluator/rank/plan` | Evaluate a plan against selected benchmarks |
| `GET` | `/api/evaluator/config` | Get evaluator configuration and available benchmarks |
| `GET` | `/api/evaluator/logo` | Get evaluator logo (base64) |

### Example

**POST `/api/evaluator/rank/plan`**: Evaluates a plan in RDA JSON format.

```bash
POST /api/evaluator/rank/plan
{
  "planModel": {
    "id": "plan-uuid",
    "title": "My Research Plan",
    "rdaJsonFile": {
      "filename": "plan.json",
      "file": "<byte array of RDA JSON content>"
    }
  },
  "benchmarkIds": [
    "684843aa21dfc4211ca0cdcf",
    "68bfe7e0d66a3009c09e6832",
    "68bff901d66a3009c09e6834"
  ]
}
```

Response:

```json
{
  "rank": 0.33,
  "results": [
    {
      "rank": 0.0,
      "benchmarkTitle": "Compliance with DMP Common Standards",
      "metrics": [
        {
          "rank": 1.0,
          "metricTitle": "Machine-Actionable Format Validation OUTPUT",
          "metricDetails": "File extension is a valid json."
        },
        {
          "rank": 0.0,
          "metricTitle": "DMP Structure Validation (JSON Schema) OUTPUT",
          "metricDetails": "Missing required fields detected: ..."
        }
      ]
    },
    {
      "rank": 1.0,
      "benchmarkTitle": "DMP FAIRness",
      "metrics": [
        {
          "rank": 1.0,
          "metricTitle": "DMP Identifier Structure Validation OUTPUT",
          "metricDetails": "dmp_id is valid."
        }
      ]
    }
  ]
}
```

---

## Configuration

Configuration is done via environment variables (or `ostrails.yml` overrides):

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `OSTRAILS_EVALUATOR_BASE_URL` | **Yes** | — | Base URL of the OSTrails DMP Evaluation Service |
| `OSTRAILS_EVALUATOR_USE_REMOTE_BENCHMARKS` | No | `true` | Fetch benchmarks from the OSTrails API at startup |
| `OSTRAILS_EVALUATOR_USE_SHARED_STORAGE` | No | `false` | Use shared storage for file handling |
| `OSTRAILS_EVALUATOR_HAS_LOGO` | No | `true` | Whether to serve a logo |
| `OSTRAILS_EVALUATOR_LOGO_PATH` | No | `classpath:ostrails.png` | Path to logo resource |

OAuth2/JWT security and server settings follow the standard OpenCDMP evaluator configuration pattern.

---

## Integration with OpenCDMP

To integrate this service with your OpenCDMP deployment, configure the evaluator plugin in OpenCDMP.

For detailed integration instructions, see the [OSTrails Evaluator Configuration](https://opencdmp.github.io/getting-started/configuration/backend/evaluators/#evaluator-ostrails) and [OpenCDMP Evaluator Service Authentication](https://opencdmp.github.io/getting-started/configuration/backend/#evaluator-service-authentication).

---

## See Also

- [OSTrails DMP Evaluation Service](https://github.com/OSTrails/DMP-Evaluation-Service) — the upstream evaluation engine this service wraps
- [OSTrails DMP Evaluation Service API](https://ostrails-dmp-evaluation.arisnet.ac.at/swagger-ui/index.html#/) — live Swagger UI
- [evaluator-base](https://github.com/OpenCDMP/evaluator-base) — the OpenCDMP evaluator interface this service implements
- [Evaluator Services Overview](https://opencdmp.github.io/optional-services/evaluator-services/) — all available OpenCDMP evaluators
- [User Guide: Evaluating Plans](https://opencdmp.github.io/user-guide/plans/evaluators/)
- [Developer Guide: Building Custom Evaluator Services](https://opencdmp.github.io/developers/plugins/evaluator/)

---

## License

This repository is licensed under the [EUPL 1.2 License](LICENSE).

---

### Contact

For questions, support, or feedback:

- **Email**: opencdmp at cite.gr
- **GitHub Issues**: https://github.com/OpenCDMP/evaluator-ostrails-dmp-evaluation-service/issues

---

*This service is part of the OpenCDMP ecosystem. For general OpenCDMP documentation, visit [opencdmp.github.io](https://opencdmp.github.io).*
