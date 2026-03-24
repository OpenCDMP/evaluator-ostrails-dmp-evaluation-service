package org.opencdmp.evaluator.ostrails.service.evaluator;


import gr.cite.tools.exception.MyApplicationException;
import gr.cite.tools.logging.LoggerService;
import gr.cite.tools.logging.MapLogEntry;
import org.opencdmp.commonmodels.enums.PluginEntityType;
import org.opencdmp.evaluator.ostrails.model.Benchmark;
import org.opencdmp.evaluator.ostrails.model.Graph;
import org.opencdmp.evaluatorbase.enums.RankType;
import org.opencdmp.evaluatorbase.enums.SuccessStatus;
import org.opencdmp.evaluatorbase.interfaces.BenchmarkConfiguration;
import org.opencdmp.evaluatorbase.interfaces.EvaluatorClient;
import org.opencdmp.evaluatorbase.interfaces.EvaluatorConfiguration;
import org.opencdmp.evaluatorbase.interfaces.SelectionConfiguration;
import org.opencdmp.evaluatorbase.models.misc.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.management.InvalidApplicationException;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequestScope
public class EvaluatorService implements EvaluatorClient {

    private static final LoggerService logger = new LoggerService(LoggerFactory.getLogger(EvaluatorService.class));
    private final EvaluatorOSTrailsServiceProperties evaluatorOstrailsServiceProperties;
    private final ResourceLoader resourceLoader;

    private byte[] logo;

    @Autowired
    public EvaluatorService(ResourceLoader resourceLoader, EvaluatorOSTrailsServiceProperties evaluatorOstrailsServiceProperties) {
        this.evaluatorOstrailsServiceProperties = evaluatorOstrailsServiceProperties;
        this.resourceLoader = resourceLoader;
    }


    @Override
    public RankResultModel rankPlan(PlanEvaluationModel planEvaluationModel) {
        if (planEvaluationModel == null || planEvaluationModel.getPlanModel() == null || planEvaluationModel.getPlanModel().getRdaJsonFile() == null ||  planEvaluationModel.getPlanModel().getRdaJsonFile().getFile() == null) throw new MyApplicationException("rda file not found!");

        if (planEvaluationModel.getBenchmarkIds() == null || planEvaluationModel.getBenchmarkIds().isEmpty()) throw new MyApplicationException("benchmark ids are empty!");

        List<BenchmarkConfiguration> availableBenchmarks = this.getAvailableBenchmarks();

        RankResultModel rankModel = new RankResultModel();
        rankModel.setRank(1);

        List<EvaluationResultModel> results = new ArrayList<>();
        for (String benchmarkId: planEvaluationModel.getBenchmarkIds()) {

            BenchmarkConfiguration benchmarkConfiguration = availableBenchmarks.stream().filter(x -> x.getId().equals(benchmarkId)).findFirst().orElse(null);

            if (benchmarkConfiguration == null) throw new MyApplicationException("not found benchmark config with id " + benchmarkId);

            if (!benchmarkConfiguration.getAppliesTo().contains(PluginEntityType.Plan)) throw new MyApplicationException("benchmark don't apply to plan");

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

            List<Benchmark> response = this.getWebClient().post()
                    .uri(this.evaluatorOstrailsServiceProperties.getBaseUrl() + "/assess/benchmark/json-ld")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.ALL)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Benchmark>>() {})
                    .block();

            if (response == null) throw new MyApplicationException("response not found for benchmark with id " + benchmarkId);

            EvaluationResultModel evaluationResultModel = new EvaluationResultModel();
            evaluationResultModel.setBenchmarkTitle(benchmarkConfiguration.getLabel());
            evaluationResultModel.setRank(1);

            List<EvaluationResultMetricModel> metrics = new ArrayList<>();
            for (Benchmark benchmark: response) {

                Graph graph = benchmark.getGraph().stream().filter(x -> x.getType().equalsIgnoreCase("ftr:TestResult")).findFirst().orElse(null);

                if (graph == null) throw new MyApplicationException("not found benchmark result with id " + benchmarkId);

                EvaluationResultMetricModel metric = new EvaluationResultMetricModel();

                if (graph.getValue() != null && graph.getValue().getValue() != null && graph.getValue().getValue().equalsIgnoreCase("pass")) {
                    // evaluation result for specific metric
                    metric.setRank(1);
                } else {
                    // entire evaluation result failed
                    evaluationResultModel.setRank(0);
                    rankModel.setRank(0);

                    // evaluation result failed for specific metric
                    metric.setRank(0);
                }

                if (graph.getDctTitle() != null) metric.setMetricTitle(graph.getDctTitle().getValue());
                if (graph.getDctDescription() != null) metric.setMetricDetails(graph.getDctDescription().getValue());
                if (graph.getLog() != null) metric.setMetricDetails(graph.getLog().getValue());

                metrics.add(metric);
            }

            if (!metrics.isEmpty()) evaluationResultModel.setMetrics(metrics);
            results.add(evaluationResultModel);
        }

        if (!results.isEmpty()) rankModel.setResults(results);

        return rankModel;
    }

    @Override
    public RankResultModel rankDescription(DescriptionEvaluationModel descriptionEvaluationModel) {
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
        return evaluatorConfiguration;
    }

    private List<BenchmarkConfiguration> getAvailableBenchmarks() {
        if (this.evaluatorOstrailsServiceProperties.isUseRemoteBenchmarks()) {
            List<Map<String, Object>> response = this.getWebClient().get()
                    .uri(this.evaluatorOstrailsServiceProperties.getBaseUrl() + "/benchmarks/list")
                    .accept(MediaType.ALL)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            if (response == null) throw new MyApplicationException("benchmarks not found");

            List<BenchmarkConfiguration> benchmarkConfigurations = new ArrayList<>();

            for (Map<String, Object> map: response) {

                BenchmarkConfiguration benchmarkConfiguration = new BenchmarkConfiguration();

                Object benchmarkId = map.getOrDefault("benchmarkId", null);
                benchmarkConfiguration.setId((String) benchmarkId);

                Object title = map.getOrDefault("title", null);
                benchmarkConfiguration.setLabel((String) title);

                benchmarkConfiguration.setAppliesTo(List.of(PluginEntityType.Plan));

                benchmarkConfigurations.add(benchmarkConfiguration);
            }
            return benchmarkConfigurations;

        } else {
            return this.evaluatorOstrailsServiceProperties.getAvailableBenchmarks();
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
        }).codecs(ClientCodecConfigurer::defaultCodecs
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

