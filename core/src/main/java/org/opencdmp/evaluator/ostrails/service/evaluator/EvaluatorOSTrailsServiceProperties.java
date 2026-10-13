package org.opencdmp.evaluator.ostrails.service.evaluator;

import org.opencdmp.commonmodels.models.ConfigurationField;
import org.opencdmp.evaluatorbase.interfaces.BenchmarkConfiguration;
import org.opencdmp.evaluatorbase.interfaces.TestConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "ostrails")
public class EvaluatorOSTrailsServiceProperties {
	private String evaluatorId;
	private boolean useSharedStorage;
	private String logo;
	private boolean hasLogo;
	private String baseUrl;
	private List<ConfigurationField> configurationFields;
	private List<ConfigurationField> userConfigurationFields;
	private boolean useRemoteBenchmarks;
	private List<BenchmarkConfiguration> availableBenchmarks;
	private boolean useRemoteTests;
	private List<TestConfiguration> availableTests;

	public String getEvaluatorId() {
		return evaluatorId;
	}

	public void setEvaluatorId(String evaluatorId) {
		this.evaluatorId = evaluatorId;
	}

	public boolean isUseSharedStorage() {
		return useSharedStorage;
	}

	public void setUseSharedStorage(boolean useSharedStorage) {
		this.useSharedStorage = useSharedStorage;
	}

	public String getLogo() {
		return logo;
	}

	public void setLogo(String logo) {
		this.logo = logo;
	}

	public boolean getHasLogo() {
		return hasLogo;
	}

	public void setHasLogo(boolean hasLogo) {
		this.hasLogo = hasLogo;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public List<ConfigurationField> getConfigurationFields() {
		return configurationFields;
	}

	public void setConfigurationFields(List<ConfigurationField> configurationFields) {
		this.configurationFields = configurationFields;
	}

	public List<ConfigurationField> getUserConfigurationFields() {
		return userConfigurationFields;
	}

	public void setUserConfigurationFields(List<ConfigurationField> userConfigurationFields) {
		this.userConfigurationFields = userConfigurationFields;
	}

	public boolean isUseRemoteBenchmarks() {
		return useRemoteBenchmarks;
	}

	public void setUseRemoteBenchmarks(boolean useRemoteBenchmarks) {
		this.useRemoteBenchmarks = useRemoteBenchmarks;
	}

	public List<BenchmarkConfiguration> getAvailableBenchmarks() {
		return availableBenchmarks;
	}

	public void setAvailableBenchmarks(List<BenchmarkConfiguration> availableBenchmarks) {
		this.availableBenchmarks = availableBenchmarks;
	}

	public boolean isUseRemoteTests() {
		return useRemoteTests;
	}

	public void setUseRemoteTests(boolean useRemoteTests) {
		this.useRemoteTests = useRemoteTests;
	}

	public List<TestConfiguration> getAvailableTests() {
		return availableTests;
	}

	public void setAvailableTests(List<TestConfiguration> availableTests) {
		this.availableTests = availableTests;
	}
}
