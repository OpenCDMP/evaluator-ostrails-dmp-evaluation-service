package org.opencdmp.evaluator.ostrails.service.evaluator;


import gr.cite.tools.exception.MyApplicationException;
import gr.cite.tools.logging.LoggerService;
import gr.cite.tools.logging.MapLogEntry;
import org.opencdmp.commonmodels.enums.PluginEntityType;
import org.opencdmp.evaluator.ostrails.model.Graph;
import org.opencdmp.evaluator.ostrails.model.Result;
import org.opencdmp.evaluator.ostrails.model.Test;
import org.opencdmp.evaluatorbase.enums.RankType;
import org.opencdmp.evaluatorbase.enums.SuccessStatus;
import org.opencdmp.evaluatorbase.interfaces.*;
import org.opencdmp.evaluatorbase.models.misc.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import io.netty.channel.ChannelOption;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.time.Duration;
import java.util.*;

@Component
@RequestScope
public class EvaluatorService implements EvaluatorClient {

    private static final LoggerService logger = new LoggerService(LoggerFactory.getLogger(EvaluatorService.class));
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final Duration RESPONSE_TIMEOUT = Duration.ofMinutes(5);
    private static final int MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024;
    private final EvaluatorOSTrailsServiceProperties evaluatorOstrailsServiceProperties;
    private final ResourceLoader resourceLoader;

    private byte[] logo;

    @Autowired
    public EvaluatorService(ResourceLoader resourceLoader, EvaluatorOSTrailsServiceProperties evaluatorOstrailsServiceProperties) {
        this.evaluatorOstrailsServiceProperties = evaluatorOstrailsServiceProperties;
        this.resourceLoader = resourceLoader;
    }


    @Override
    public List<RankResultModel> rankPlan(PlanEvaluationModel planEvaluationModel) {
        if (planEvaluationModel == null || planEvaluationModel.getPlanModel() == null || planEvaluationModel.getPlanModel().getRdaJsonFile() == null ||  planEvaluationModel.getPlanModel().getRdaJsonFile().getFile() == null) throw new MyApplicationException("rda file not found!");

        if ((planEvaluationModel.getBenchmarkIds() == null || planEvaluationModel.getBenchmarkIds().isEmpty()) && (planEvaluationModel.getTestIds() == null || planEvaluationModel.getTestIds().isEmpty())) throw new MyApplicationException("benchmark and tests ids are empty!");

        RankResultModel rankModel = new RankResultModel();
        rankModel.setRank(1);

        List<EvaluationResultModel> results = new ArrayList<>();

        if (planEvaluationModel.getBenchmarkIds() != null && !planEvaluationModel.getBenchmarkIds().isEmpty()) {
            List<BenchmarkConfiguration> availableBenchmarks = Objects.requireNonNullElse(this.getAvailableBenchmarks(), List.of());
            for (String benchmarkId : planEvaluationModel.getBenchmarkIds()) {

                BenchmarkConfiguration benchmarkConfiguration = availableBenchmarks.stream().filter(x -> x.getId().equals(benchmarkId)).findFirst().orElse(null);

                if (benchmarkConfiguration == null)
                    throw new MyApplicationException("not found benchmark config with id " + benchmarkId);

                if (!benchmarkConfiguration.getAppliesTo().contains(PluginEntityType.Plan))
                    throw new MyApplicationException("benchmark don't apply to plan");

                ByteArrayResource resource = new ByteArrayResource(planEvaluationModel.getPlanModel().getRdaJsonFile().getFile()) {
                    @Override
                    public String getFilename() {
                        return planEvaluationModel.getPlanModel().getRdaJsonFile().getFilename();
                    }
                };


                MultipartBodyBuilder builder = new MultipartBodyBuilder();
                builder.part("maDMP", resource).contentType(MediaType.APPLICATION_JSON);
                builder.part("benchmark", benchmarkId);
                builder.part("reportId", "");

                List<Result> benchmarkResults = this.getWebClient().post()
                        .uri(this.evaluatorOstrailsServiceProperties.getBaseUrl() + "/assess/benchmark")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.ALL)
                        .body(BodyInserters.fromMultipartData(builder.build()))
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<Result>>() {})
                        .block();

                if (benchmarkResults != null && !benchmarkResults.isEmpty()) {
                    for (Result result: benchmarkResults) {
                        this.buildEvaluationResultModel(benchmarkConfiguration.getLabel(), result, rankModel, results);
                    }
                } else {
                    // a benchmark that ran no tests must not count as a pass
                    this.buildEvaluationResultModel(benchmarkConfiguration.getLabel(), null, rankModel, results);
                }

            }
        }

        if (planEvaluationModel.getTestIds() != null && !planEvaluationModel.getTestIds().isEmpty()) {
            List<TestConfiguration> availableTests = Objects.requireNonNullElse(this.getAvailableTests(), List.of());
            for (String testId: planEvaluationModel.getTestIds()) {

                TestConfiguration testConfiguration = availableTests.stream().filter(x -> x.getId().equals(testId)).findFirst().orElse(null);

                if (testConfiguration == null)
                    throw new MyApplicationException("not found test config with id " + testId);

                if (!testConfiguration.getAppliesTo().contains(PluginEntityType.Plan))
                    throw new MyApplicationException("test don't apply to plan");

                ByteArrayResource resource = new ByteArrayResource(planEvaluationModel.getPlanModel().getRdaJsonFile().getFile()) {
                    @Override
                    public String getFilename() {
                        return planEvaluationModel.getPlanModel().getRdaJsonFile().getFilename();
                    }
                };

                MultipartBodyBuilder builder = new MultipartBodyBuilder();
                builder.part("maDMP", resource).contentType(MediaType.APPLICATION_JSON);
                builder.part("test", testId);
                builder.part("reportId", "");

                Result result = this.getWebClient().post()
                        .uri(this.evaluatorOstrailsServiceProperties.getBaseUrl() + "/assess/test")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.ALL)
                        .body(BodyInserters.fromMultipartData(builder.build()))
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Result>() {
                        })
                        .block();


                this.buildEvaluationResultModel(testConfiguration.getLabel(), result, rankModel, results);

            }
        }


        if (!results.isEmpty()) rankModel.setResults(results);

        return List.of(rankModel);
    }

    private void buildEvaluationResultModel(String evaluationResultModelLabel, Result result, RankResultModel rankModel, List<EvaluationResultModel> results) {
        EvaluationResultModel evaluationResultModel = new EvaluationResultModel();
        evaluationResultModel.setBenchmarkTitle(evaluationResultModelLabel);
        evaluationResultModel.setRank(1);

        List<EvaluationResultMetricModel> metrics = new ArrayList<>();

        EvaluationResultMetricModel metric = new EvaluationResultMetricModel();

        if (result != null && result.getValue() != null && result.getValue().equalsIgnoreCase("pass")) {
            // evaluation result for specific metric
            metric.setRank(1);
        } else {
            // entire evaluation result failed
            evaluationResultModel.setRank(0);
            rankModel.setRank(0);

            // evaluation result failed for specific metric
            metric.setRank(0);
        }

        if (result == null) {
            metric.setMetricDetails("No results returned");
        } else {
            if (result.getTitle() != null) metric.setMetricTitle(result.getTitle());
            List<String> details = new ArrayList<>();
            if (result.getDescription() != null && !result.getDescription().isBlank()) details.add(result.getDescription());
            if (result.getLog() != null && !result.getLog().isBlank()) details.add(result.getLog());
            if (!details.isEmpty()) metric.setMetricDetails(String.join("\n", details));
        }

        metrics.add(metric);

        evaluationResultModel.setMetrics(metrics);
        results.add(evaluationResultModel);
    }

    @Override
    public List<RankResultModel> rankDescription(DescriptionEvaluationModel descriptionEvaluationModel) {
        throw new UnsupportedOperationException("rank description not supported");
    }

    @Override
    public EvaluatorConfiguration getConfiguration() {
        EvaluatorConfiguration evaluatorConfiguration = new EvaluatorConfiguration();
        evaluatorConfiguration.setEvaluatorId(evaluatorOstrailsServiceProperties.getEvaluatorId());
        evaluatorConfiguration.setEvaluatorEntityTypes(List.of(PluginEntityType.Plan));
        evaluatorConfiguration.setUseSharedStorage(evaluatorOstrailsServiceProperties.isUseSharedStorage());
        evaluatorConfiguration.setHasLogo(this.evaluatorOstrailsServiceProperties.getHasLogo());
        evaluatorConfiguration.setConfigurationFields(this.evaluatorOstrailsServiceProperties.getConfigurationFields());
        evaluatorConfiguration.setUserConfigurationFields(this.evaluatorOstrailsServiceProperties.getUserConfigurationFields());
        evaluatorConfiguration.setRankConfig(new RankConfig());
        evaluatorConfiguration.getRankConfig().setRankType(RankType.Selection);
        evaluatorConfiguration.getRankConfig().setSelectionConfiguration(new SelectionConfiguration());
        SelectionConfiguration.ValueSet valueSetSuccess = new SelectionConfiguration.ValueSet();
        valueSetSuccess.setKey(1);
        valueSetSuccess.setSuccessStatus(SuccessStatus.Pass);

        SelectionConfiguration.ValueSet valueSetFail = new SelectionConfiguration.ValueSet();
        valueSetFail.setKey(0);
        valueSetFail.setSuccessStatus(SuccessStatus.Fail);
        evaluatorConfiguration.getRankConfig().getSelectionConfiguration().setValueSetList(Arrays.asList(valueSetSuccess, valueSetFail));

        evaluatorConfiguration.setAvailableBenchmarks(this.getAvailableBenchmarks());
        evaluatorConfiguration.setAvailableTests(this.getAvailableTests());
        return evaluatorConfiguration;
    }

    private List<BenchmarkConfiguration> getAvailableBenchmarks() {
        if (this.evaluatorOstrailsServiceProperties.isUseRemoteBenchmarks()) {
            List<BenchmarkConfiguration> benchmarkConfigurations = new ArrayList<>();

            List<Graph> benchmarksList = this.getWebClient().get()
                    .uri(this.evaluatorOstrailsServiceProperties.getBaseUrl() + "/benchmarks/list/jsonLD")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Graph>>() {})
                    .block();

            if (benchmarksList == null) benchmarksList = new ArrayList<>();

            for (Graph graph: benchmarksList) {

                BenchmarkConfiguration benchmarkConfiguration = new BenchmarkConfiguration();
                benchmarkConfiguration.setId(graph.getIdentifier().getId().substring(graph.getIdentifier().getId().lastIndexOf('/') + 1));
                benchmarkConfiguration.setLabel(graph.getDctTitle().getValue());
                benchmarkConfiguration.setAppliesTo(List.of(PluginEntityType.Plan));
                benchmarkConfigurations.add(benchmarkConfiguration);

            }

            return benchmarkConfigurations;

        } else {
            return this.evaluatorOstrailsServiceProperties.getAvailableBenchmarks();
        }
    }

    private List<TestConfiguration> getAvailableTests() {
        if (this.evaluatorOstrailsServiceProperties.isUseRemoteTests()) {
            List<TestConfiguration> testConfigurations = new ArrayList<>();

            Object object = this.getWebClient().get()
                    .uri(this.evaluatorOstrailsServiceProperties.getBaseUrl() + "/tests/list")
                    .accept(MediaType.ALL)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Object>() {})
                    .block();

            if (object instanceof List) {
                ObjectMapper mapper = new ObjectMapper();

                List<Test> testList = mapper.convertValue(object, new TypeReference<List<Test>>() {}
                );

                if (testList == null) testList = new ArrayList<>();

                for (Test test: testList) {
                    TestConfiguration testConfiguration = new TestConfiguration();
                    testConfiguration.setId(test.getIdentifier());
                    testConfiguration.setLabel(test.getTitle());
                    testConfiguration.setAppliesTo(List.of(PluginEntityType.Plan));
                    testConfigurations.add(testConfiguration);
                }
            }

            return testConfigurations;

        } else {
            return this.evaluatorOstrailsServiceProperties.getAvailableTests();
        }
    }

    @Override
    public String getLogo() {
        if(this.evaluatorOstrailsServiceProperties != null && this.evaluatorOstrailsServiceProperties.getHasLogo() && this.evaluatorOstrailsServiceProperties.getLogo() != null && !this.evaluatorOstrailsServiceProperties.getLogo().isBlank()){
            if(this.logo == null){
                try{
                    Resource resource = this.resourceLoader.getResource(this.evaluatorOstrailsServiceProperties.getLogo());
                    if(!resource.isReadable()) return null;
                    try(InputStream inputStream = resource.getInputStream()) {
                        this.logo = inputStream.readAllBytes();
                    }
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }
            return (this.logo != null && this.logo.length != 0) ? Base64.getEncoder().encodeToString(this.logo) : null;
        }
        return null;
    }

    private WebClient getWebClient(){
        return WebClient.builder().filters(exchangeFilterFunctions -> {
            exchangeFilterFunctions.add(logRequest());
            exchangeFilterFunctions.add(logResponse());
        }).clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .responseTimeout(RESPONSE_TIMEOUT))
        ).codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE)
        ).build();
    }

    private static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            logger.debug(new MapLogEntry("Request").And("method", clientRequest.method().toString()).And("url", clientRequest.url().toString()));
            return Mono.just(clientRequest);
        });
    }

    private static ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            if (response.statusCode().isError()) {
                return response.mutate().build().bodyToMono(String.class)
                        .flatMap(body -> {
                            logger.error(new MapLogEntry("Response").And("method", response.request().getMethod().toString()).And("url", response.request().getURI()).And("status", response.statusCode().toString()).And("body", body));
                            return Mono.just(response);
                        });
            }
            return Mono.just(response);

        });
    }
}

