package com.lgi.catalog.metrics;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class PrometheusMetricsServlet extends HttpServlet {

    private final PrometheusMeterRegistry registry;

    public PrometheusMetricsServlet(PrometheusMeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain; version=0.0.4; charset=utf-8");
        resp.getWriter().write(registry.scrape());
    }
}
