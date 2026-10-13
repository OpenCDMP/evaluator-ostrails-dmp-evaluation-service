package org.opencdmp.evaluator.ostrails.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties
public class Graph {

    @JsonProperty(value="dcterms:title")
    private Label dctTitle;

    @JsonProperty(value="dcterms:identifier")
    private Identifier identifier;


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

    public static class Identifier {

        @JsonProperty(value="@id")
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public Label getDctTitle() {
        return dctTitle;
    }

    public void setDctTitle(Label dctTitle) {
        this.dctTitle = dctTitle;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public void setIdentifier(Identifier identifier) {
        this.identifier = identifier;
    }
}
