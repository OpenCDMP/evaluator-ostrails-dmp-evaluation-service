package org.opencdmp.evaluator.ostrails.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties
public class Result {

    private String identifier;

    private String title;

    private String description;

    private String generatedAtTime;

    private String value;

    private String log;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGeneratedAtTime() {
        return generatedAtTime;
    }

    public void setGeneratedAtTime(String generatedAtTime) {
        this.generatedAtTime = generatedAtTime;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }
}
