package uk.co.crunch

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import java.io.StringWriter
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces

@Path("/prometheusMetrics")
class PrometheusEndpoint(private val registry: CollectorRegistry) {

    @GET
    @Produces(TextFormat.CONTENT_TYPE_004)
    fun prometheusMetrics() = run {
        // FIXME Why can't we access response Writer rather than cache ourselves?
        StringWriter().also { TextFormat.write004(it, registry.metricFamilySamples()) }.toString()
    }
}
