package uk.co.crunch.api

import io.swagger.annotations.Api
import org.eclipse.microprofile.graphql.Description
import org.eclipse.microprofile.graphql.GraphQLApi
import org.eclipse.microprofile.graphql.Name
import org.eclipse.microprofile.graphql.Query
import uk.co.crunch.K8s
import uk.co.crunch.api.kotlin.PrometheusMetrics
import uk.co.crunch.persistence.FactService
import uk.co.crunch.persistence.entity.Fact
import uk.co.crunch.persistence.entity.FactTypes
import uk.co.crunch.platform.api.tyk.WhitelistStaticResources

// https://quarkus.io/guides/microprofile-graphql
@GraphQLApi
@Api("GraphQLApi")
@WhitelistStaticResources("/graphql")  // Not really static, but...
class FactResource(private val facts: FactService, private val metrics: PrometheusMetrics) {

    @Query("all")
    @Description("Get all facts")
    fun getAllFacts(): List<Fact> = metrics.timed("getAllFacts").use {
        facts.entities.createQuery("FROM Fact f", Fact::class.java).resultList
    }

    @Query("byName")
    @Description("Get all facts by name")
    fun getFactsByName(@Name("name") name: String?): List<Fact> = metrics.timed("getFactsByName").use {
        requireNotNull(name) { "Name must not be null" }
        facts.entities.createQuery("FROM Fact f WHERE name=:n", Fact::class.java).setParameter("n", name).resultList
    }

    @Query("search")
    @Description("Search by name, type, or tag")
    fun searchByNameTypeOrTag(@Name("query") query: String?): SearchRepresentation = metrics.timed("searchByNameTypeOrTag").use {
        requireNotNull(query) { "Name must not be null" }
        SearchRepresentation(
            query,
            facts.entities.createQuery(
                "FROM Fact f WHERE name=:q OR type=:q OR :q IN elements(f.tags) OR :q IN elements(f.synonyms)",
                Fact::class.java
            ).setParameter("q", query).resultList
        )
    }

    @Suppress("unused")
    class SearchRepresentation(private val query: String, facts: List<Fact>) {
        var facets: List<FactRepresentation> = facts.map { FactRepresentation(query, it) }.sortedBy { it }
    }

    @Suppress("unused")
    class FactRepresentation(query: String, fact: Fact) : Comparable<FactRepresentation> {
        val name = if (fact.type == FactTypes.LOGZ_URL) logzUrl(fact.name) else fact.name
        val type = fact.type
        val description = fact.description
        val extraTags = fact.tags.filter { it.name != query }.map { it.name }  // strip out tags where it matches our query
        val synonyms = fact.synonyms
        val updatedDateTime = fact.updatedDateTime

        private fun logzUrl(releaseName: String) = LOGZ_URL.replace("_release_", releaseName).replace("_env_", K8s.ENVIRONMENT)

        override fun compareTo(other: FactRepresentation) = run {
            // Put custom labels last
            val name1 = FactTypes.CUSTOM_ORDERING.indexOf(this.type).takeIf { it >= 0 } ?: 999
            val name2 = FactTypes.CUSTOM_ORDERING.indexOf(other.type).takeIf { it >= 0 } ?: 999
            name1.compareTo(name2)
        }
    }

    companion object {
        const val LOGZ_URL =
            "https://app.logz.io/#/dashboard/kibana/discover?_a=(columns:!(message),filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:'logzioCustomerIndex*',key:kubernetes.labels.release,negate:!f,params:(query:_release_),type:phrase),query:(match_phrase:(kubernetes.labels.release:_release_))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:'logzioCustomerIndex*',key:environment,negate:!f,params:(query:_env_),type:phrase),query:(match_phrase:(environment:_env_)))),index:'logzioCustomerIndex*',interval:auto,query:(language:lucene,query:''),sort:!(!('@timestamp',desc)))&_g=(filters:!(),refreshInterval:(pause:!f,value:60000),time:(from:now-30m,to:now))&accountIds=48245&accountIds=53195&switchToAccountId=48245";
    }
}
