package org.opencdmp.evaluator.ostrails.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Benchmark {

    @JsonProperty(value="@graph")
    private List<Graph> graph;

    public List<Graph> getGraph() {
        return graph;
    }

    public void setGraph(List<Graph> graph) {
        this.graph = graph;
    }
}
