package uk.co.crunch.persistence

import org.jboss.logging.Logger
import uk.co.crunch.api.kotlin.PrometheusMetrics
import uk.co.crunch.persistence.entity.Fact
import uk.co.crunch.persistence.entity.FactSource
import javax.enterprise.context.ApplicationScoped
import javax.persistence.EntityManager
import javax.transaction.Transactional

@ApplicationScoped
class FactService(val entities: EntityManager, val metrics: PrometheusMetrics) {

    @Transactional
    fun zeroFactSourceServiceTags(source: FactSource, serviceReleaseName: String) = run {
        val query = "FROM Fact WHERE source='$source' AND ownerRelease='$serviceReleaseName'"
        this.entities.createQuery(query, Fact::class.java).resultList.forEach {
            this.entities.remove(it)
        }
    }

    @Transactional
    fun query(q: String): Fact? {
        return entities.createQuery(q, Fact::class.java).resultList.firstOrNull()
    }

    // Seems to be required @ https://quarkus.io/guides/hibernate-orm
    @Transactional
    fun saveFact(f: Fact) {
        metrics.timed("saveFact").use {
            // LOG.debug(">> Adding $f")
            f.id?.let { this.entities.merge(f) } ?: run { this.entities.persist(f) }
        }
    }

    companion object {
        @Suppress("unused")
        private val LOG: Logger = Logger.getLogger(FactService::class.java)
    }
}
