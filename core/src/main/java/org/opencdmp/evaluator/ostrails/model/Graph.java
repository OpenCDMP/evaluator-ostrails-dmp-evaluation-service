package org.opencdmp.evaluator.ostrails.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Graph {

    @JsonProperty(value="@id")
    private String id;

    @JsonProperty(value="@type")
    private String type;

    @JsonProperty(value="dct:title")
    private Label dctTitle;

    @JsonProperty(value="dct:description")
    private Label dctDescription;

    @JsonProperty(value="prov:value")
    private Label value;

    @JsonProperty(value="ftr:log")
    private Label log;

    public static class Label {

        @JsonProperty(value="@language")
        private String language;

        @JsonProperty(value="@value")
        private String value;

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Label getValue() {
        return value;
    }

    public void setValue(Label value) {
        this.value = value;
    }

    public Label getLog() {
        return log;
    }

    public void setLog(Label log) {
        this.log = log;
    }

    public Label getDctTitle() {
        return dctTitle;
    }

    public void setDctTitle(Label dctTitle) {
        this.dctTitle = dctTitle;
    }

    public Label getDctDescription() {
        return dctDescription;
    }

    public void setDctDescription(Label dctDescription) {
        this.dctDescription = dctDescription;
    }
}
