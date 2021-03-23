package uk.co.crunch

import io.prometheus.client.CollectorRegistry
import uk.co.crunch.api.kotlin.PrometheusMetrics
import javax.inject.Singleton

@Suppress("unused")
class Producers {
    @Singleton
    fun produceCollectorRegistry(): CollectorRegistry = CollectorRegistry.defaultRegistry

    // Should match `quarkus.http.root-path` ... inject?
    @Singleton
    fun producePrometheusMetrics(registry: CollectorRegistry) = PrometheusMetrics(registry, "affinity-meta")
}
