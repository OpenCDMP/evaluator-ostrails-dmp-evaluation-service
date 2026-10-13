# OpenCDMP OSTrails DMP evaluator

**evaluator-ostrails-dmp-evaluation-service** evaluates a plan's RDA JSON export with the [OSTrails DMP Evaluation Service](https://github.com/OSTrails/DMP-Evaluation-Service), an open-source engine that runs benchmarks and tests on machine-actionable Data Management Plans (maDMPs). It forwards the export to an OSTrails instance and turns the OSTrails results into OpenCDMP evaluation results.

It is an evaluator plugin for [OpenCDMP](https://github.com/OpenCDMP/OpenCDMP), the open platform for Data Management Plans and other Output Management Plans: a user starts an evaluation from a plan, OpenCDMP calls the evaluator over HTTP, and the result appears in OpenCDMP. The service implements the interfaces of [evaluator-base](https://github.com/OpenCDMP/evaluator-base); its evaluator id is `ostrails`. See [Evaluator services](https://opencdmp.github.io/optional-services/evaluator-services/#evaluator-ostrails) in the OpenCDMP documentation.

## What it checks

- OpenCDMP sends the plan's RDA JSON export, produced by the [RDA JSON file transformer](https://github.com/OpenCDMP/file-transformer-rda-json). The evaluator reads only that file.
- The OSTrails service defines the checks. A benchmark is a named group of checks, such as compliance with the RDA DMP Common Standard; a test is one check that users can select on its own. Users can select benchmarks, tests, or both.
- For each selected benchmark, the evaluator posts the export to `<OSTRAILS_EVALUATOR_BASE_URL>/assess/benchmark`; for each selected test, to `/assess/test`.
- Each test result that OSTrails returns becomes one result, titled after the benchmark or test it came from, with a pass (`1`) or fail (`0`) and the OSTrails title, description and log as details. A benchmark that returns no test results counts as failed. The plan passes only if every result passes.
- Only plans can be evaluated. Asset evaluation (`/rank/description`) is not supported.

What OSTrails sees depends on what the RDA export contains; see the [RDA JSON semantics reference](https://opencdmp.github.io/optional-services/file-transformers/rda-json-semantics/).

## Requirements

- An OpenCDMP installation and its Keycloak realm. The service accepts only tokens that this realm issued with the `plugins` audience.
- An OSTrails DMP Evaluation Service the evaluator can reach. Run your own from the [OSTrails repository](https://github.com/OSTrails/DMP-Evaluation-Service), or use a public instance such as `https://ostrails-dmp-evaluation.arisnet.ac.at` ([API](https://ostrails-dmp-evaluation.arisnet.ac.at/swagger-ui/index.html)).
- To build from source: Java 21 and Maven. The service runs on Spring Boot 4.0 and uses `evaluator-base`; the version is in `pom.xml`.

## Run with Docker

The image is [`opencdmp/evaluator-ostrails-dmp-evaluation-service`](https://hub.docker.com/r/opencdmp/evaluator-ostrails-dmp-evaluation-service) on Docker Hub. It listens on port `8080`, reads `app-<PROFILE>.env` from `/config` (set the `PROFILE` environment variable), and writes its logs to `/logs`. [Registering plugin services](https://opencdmp.github.io/deployment/configuration/plugin-services/#step-1-run-the-service) shows a Docker Compose entry.

At startup the service downloads the OpenID configuration from `IDP_ISSUER_URI`, so Keycloak must be reachable at that address. `/actuator/health` reports when the service is up.

## Build from source

```bash
mvn clean package
```

The build produces `web/target/evaluator-ostrails-web-1.0.0-SNAPSHOT.jar`. To run it, set the required variables and start it:

```bash
export IDP_ISSUER_URI=https://opencdmp.example.org/auth/realms/OpenCDMP
export IDP_CLAIMS_ROLES_PATH=plugins.roles
export SECURITY_AUDIENCE=plugins
export LOGGING_PATH=./logs
export OSTRAILS_EVALUATOR_BASE_URL=https://ostrails-dmp-evaluation.arisnet.ac.at
java -jar web/target/evaluator-ostrails-web-1.0.0-SNAPSHOT.jar
```

Without `SERVER_PORT`, the service listens on port `8079`; check it with `curl http://localhost:8079/actuator/health`. To call it with a token and try it with a local OpenCDMP installation, see [Run and test a plugin locally](https://opencdmp.github.io/developers/plugins/run-locally/).

The `Dockerfile` builds the service from source:

```bash
docker build -t evaluator-ostrails-dmp-evaluation-service .
```

## Configuration

The service reads its settings from environment variables. It needs the [settings common to all plugin services](https://opencdmp.github.io/deployment/configuration/plugin-services/#settings-common-to-all-plugin-services): `IDP_ISSUER_URI`, `IDP_CLAIMS_ROLES_PATH` (`plugins.roles`) and `SECURITY_AUDIENCE` (`plugins`) for authentication, `PROFILE`, and the logging settings. Its own settings are:

| Variable | Purpose | Default |
| --- | --- | --- |
| `OSTRAILS_EVALUATOR_BASE_URL` | Base URL of the OSTrails DMP Evaluation Service. Required. | - |
| `OSTRAILS_EVALUATOR_USE_REMOTE_BENCHMARKS` | Offer the benchmarks that the OSTrails service lists (`/benchmarks/list/jsonLD`). When `false`, offer the `availableBenchmarks` of `ostrails.yml`; the bundled file lists one, `Compliance with DMP Common Standards`. | `true` |
| `OSTRAILS_EVALUATOR_USE_REMOTE_TESTS` | Offer the tests that the OSTrails service lists (`/tests/list`). When `false`, offer the `availableTests` of `ostrails.yml`; the bundled file lists none. | `true` |
| `OSTRAILS_EVALUATOR_HAS_LOGO`, `OSTRAILS_EVALUATOR_LOGO_PATH` | Logo shown next to the evaluator in OpenCDMP | `true`, `classpath:ostrails.png` |
| `OSTRAILS_EVALUATOR_USE_SHARED_STORAGE` | Reported to OpenCDMP, but this evaluator reads no shared folder. Leave it `false`. | `false` |

[Evaluator services configuration](https://opencdmp.github.io/deployment/configuration/backend/evaluators/#evaluator-ostrails) has the full reference.

### Register with OpenCDMP

Register the service with its address in the API service configuration under `evaluator.sources`, with `evaluatorId: ostrails` and the RDA JSON transformer (`rda-file-transformer`) as `rdaTransformerId`, or for one organisation under **Settings > Plugins**. The API service calls the evaluator with a client-credentials token that carries the `plugins` audience. See [Registering plugin services](https://opencdmp.github.io/deployment/configuration/plugin-services/).

## API

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/evaluator/rank/plan` | Evaluate a plan against the selected benchmarks and tests |
| `POST` | `/api/evaluator/rank/description` | Not supported; returns an error |
| `GET` | `/api/evaluator/config` | Evaluator id, available benchmarks and tests, and how ranks map to pass or fail |
| `GET` | `/api/evaluator/logo` | Logo as base64, if one is configured |

The `/api` endpoints require a bearer token. `/actuator/health`, `/actuator/info`, `/actuator/metrics` and `/actuator/prometheus` are open, so keep the port on an internal network.

Request (`file` is the RDA JSON export, base64-encoded; give at least one of `benchmarkIds` and `testIds`, using the ids that `/api/evaluator/config` lists):

```json
{
  "planModel": {
    "id": "0b7f4a8e-5d3c-4a51-9f2e-6c1d2b3a4e5f",
    "label": "My research plan",
    "rdaJsonFile": {
      "filename": "plan.json",
      "mimeType": "application/json",
      "file": "<base64-encoded RDA JSON>"
    }
  },
  "benchmarkIds": ["684843aa21dfc4211ca0cdcf"],
  "testIds": ["68c00a1bd66a3009c09e6841"]
}
```

The response is a one-element list with the result for the whole plan. Each OSTrails test result is one entry of `results`, with a single metric:

```json
[
  {
    "rank": 0.0,
    "results": [
      {
        "rank": 1.0,
        "benchmarkTitle": "Compliance with DMP Common Standards",
        "metrics": [
          {
            "rank": 1.0,
            "metricTitle": "Machine-Actionable Format Validation OUTPUT",
            "metricDetails": "File extension is a valid json."
          }
        ]
      },
      {
        "rank": 0.0,
        "benchmarkTitle": "Compliance with DMP Common Standards",
        "metrics": [
          {
            "rank": 0.0,
            "metricTitle": "DMP Structure Validation (JSON Schema) OUTPUT",
            "metricDetails": "Missing required fields detected: ..."
          }
        ]
      }
    ]
  }
]
```

The [evaluator plugin guide](https://opencdmp.github.io/developers/plugins/evaluator/) and [Common models](https://opencdmp.github.io/developers/plugins/common-models/) describe the request and result models.

## Related resources

- [Evaluator services](https://opencdmp.github.io/optional-services/evaluator-services/#evaluator-ostrails): what each OpenCDMP evaluator checks
- [Evaluating plans](https://opencdmp.github.io/using/plans/evaluators/): running an evaluation and reading the result in OpenCDMP
- [Evaluator plugin guide](https://opencdmp.github.io/developers/plugins/evaluator/): building your own evaluator
- [OSTrails DMP Evaluation Service](https://github.com/OSTrails/DMP-Evaluation-Service): the evaluation engine this service calls
- [OpenCDMP documentation](https://opencdmp.github.io/)

## Contributing

Report bugs and suggest changes in [GitHub Issues](https://github.com/OpenCDMP/evaluator-ostrails-dmp-evaluation-service/issues). See [Contributing](https://opencdmp.github.io/developers/contributing/) for how to propose a change.

## License

evaluator-ostrails-dmp-evaluation-service is licensed under the [European Union Public Licence (EUPL) v1.2](LICENSE).

## Contact

Questions: email opencdmp at cite.gr.
