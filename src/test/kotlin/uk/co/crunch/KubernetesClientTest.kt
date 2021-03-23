package uk.co.crunch

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThanOrEqualTo
import uk.co.crunch.persistence.FactService
import uk.co.crunch.persistence.entity.Fact
import uk.co.crunch.persistence.entity.FactSource
import uk.co.crunch.persistence.entity.FactTypes
import uk.co.crunch.persistence.entity.FactTypes.RABBIT_LISTENER_ID
import javax.inject.Inject

@QuarkusTestResource(CustomKubernetesMockServerTestResource::class)
@QuarkusTest
class KubernetesClientTest {

    @Inject lateinit var facts: FactService

    companion object {
        @BeforeAll
        internal fun waitForK8s() {
            Thread.sleep(1000)  // Yuk, FIXME
        }
    }

    @Test
    fun `exchange lookup with ordering`() {
        expectThat(
            facts.entities.createQuery(
                "FROM Fact f WHERE type='RABBIT_EXCHANGE' ORDER BY name",
                Fact::class.java
            ).resultList.map { it.name }).isEqualTo(listOf("americano", "cappuccino", "latte", "tea"))
    }

    @Test
    fun `queue lookup with ordering`() {
        expectThat(
            facts.entities.createQuery(
                "FROM Fact f WHERE type='RABBIT_QUEUE' ORDER BY name",
                Fact::class.java
            ).resultList.map { it.name }).isEqualTo(
            listOf(
                "consumer.latte.producer.latte.check-exists",
                "consumer.latte.producer.latte.count",
                "consumer.latte.producer.latte.create",
                "consumer.latte.producer.latte.delete",
                "consumer.latte.producer.latte.list",
                "consumer.latte.producer.latte.read",
                "consumer.latte.producer.latte.rename",
                "consumer.latte.producer.latte.zip",
                "supplier.create",
                "suppliers.get"
            )
        )
    }

    @Test
    fun `all items tagged with 'cappuccino-service'`() {
        expectThat(
            facts.entities.createQuery(
                "FROM Fact f WHERE 'crunch-cappuccino-service' in elements(f.tags) ORDER BY type,name",
                Fact::class.java
            ).resultList.map { "${it.type}:${it.name}" }).isEqualTo(
            listOf(
                "BB_REPO:https://bitbucket.org/crunch-ondemand/crunch-cappuccino-service",
                "CLOUD_CONFIG_PROFILE:americano-api",
                "CLOUD_CONFIG_PROFILE:public-api",
                "GIT_BRANCH:my-special-branch",
                "LOGZ_URL:crunch-cappuccino-service",  // different for GraphQL
                "MYSQL_SCHEMA_NAME:mydb",
                "OWNER:MyTeam",
                "PARENT_POM_VERSION:5.6.47",
                "PRODUCT:Drinks",
                "RABBIT_EXCHANGE:americano",
                "RABBIT_EXCHANGE:cappuccino",
                "RABBIT_EXCHANGE:latte",
                "RABBIT_EXCHANGE:tea",
                "RABBIT_LISTENER_COUNT:2",
                "RABBIT_LISTENER_ID:createSupplierListener",
                "RABBIT_LISTENER_ID:getSuppliersListener",
                "RABBIT_QUEUE:supplier.create",
                "RABBIT_QUEUE:suppliers.get",
                "SPRINGBOOT_VERSION:2.4.2",
                "STACK:Consumables",
                "foo:bar",
                "myLabel:myLabelValue"
            )
        )
    }

    @Test
    fun `results for a named listener id`() {
        expectThat(
            facts.entities.createQuery(
                "FROM Fact f WHERE name='createSupplierListener'",
                Fact::class.java
            ).resultList.map { "${it.description} >> ${it.tags.map { (name) -> name }}" })
            .isEqualTo(
                listOf("@RabbitListener createSupplierListener >> [crunch-cappuccino-service, infra:rabbitmq]")
            )
    }

    @Test
    fun `all fact fields for name lookup`() {
        expectThat(facts.entities.createQuery("FROM Fact f WHERE name='latte'", Fact::class.java).singleResult) {
            get { name }.isEqualTo("latte")
            get { tags.map { it.name } }.isEqualTo(mutableListOf("crunch-cappuccino-service", "infra:rabbitmq", "latte-service"))
            get { description }.isEqualTo("Exchange latte")
            get { type }.isEqualTo(FactTypes.RABBIT_EXCHANGE)
            get { source }.isEqualTo(FactSource.RABBIT_TOPOLOGY)
            get { updatedDateTime.year }.isGreaterThanOrEqualTo(2021)
        }
    }

    @Test
    fun `expected fact count`() {
        expectThat(facts.entities.createQuery("SELECT COUNT(*) FROM Fact f").singleResult).isEqualTo(39L)
    }

    /**
     * Port-forward from pod to :8080 then
     *
     * > http post localhost:8080/names/graphql query='{ byName(name:"latte") { name, tags { name }, type, source } }'
     */
    @Test
    fun graphQlFactsByName() {
        val query = GraphQlQuery(query = "{ byName(name:\"latte\") { name, tags { name }, type, source } }")

        given().`when`().body(query).post("/graphql")
            .then()
            .statusCode(200)
            .body(`is`("{\"data\":{\"byName\":[{\"name\":\"latte\",\"tags\":[{\"name\":\"infra:rabbitmq\"},{\"name\":\"crunch-cappuccino-service\"},{\"name\":\"latte-service\"}],\"type\":\"RABBIT_EXCHANGE\",\"source\":\"RABBIT_TOPOLOGY\"}]}}"))
    }

    @Test
    fun graphQlSearch() {
        val query = GraphQlQuery(query = "{ search(query:\"cappuccino\") { facets { type, description } } } ")

        given().`when`().body(query).post("/graphql")
            .then()
            .statusCode(200)
            .body(
                `is`(
                    "{\"data\":{\"search\":{\"facets\":[{\"type\":\"PRODUCT\",\"description\":\"Product: Drinks\"},{\"type\":\"STACK\",\"description\":\"Consumables stack\"},{\"type\":\"OWNER\",\"description\":\"Owned by MyTeam\"},{\"type\":\"BB_REPO\",\"description\":\"BitBucket repo https://bitbucket.org/crunch-ondemand/crunch-cappuccino-service\"},{\"type\":\"CLOUD_CONFIG_PROFILE\",\"description\":\"americano-api Cloud Config profile\"},{\"type\":\"CLOUD_CONFIG_PROFILE\",\"description\":\"public-api Cloud Config profile\"},{\"type\":\"MYSQL_SCHEMA_NAME\",\"description\":\"Schema mydb\"},{\"type\":\"RABBIT_EXCHANGE\",\"description\":\"Exchange latte\"},{\"type\":\"RABBIT_EXCHANGE\",\"description\":\"Exchange americano\"},{\"type\":\"RABBIT_EXCHANGE\",\"description\":\"Exchange tea\"},{\"type\":\"RABBIT_EXCHANGE\",\"description\":\"Exchange cappuccino\"},{\"type\":\"RABBIT_QUEUE\",\"description\":\"Queue supplier.create\"},{\"type\":\"RABBIT_QUEUE\",\"description\":\"Queue suppliers.get\"},{\"type\":\"RABBIT_LISTENER_COUNT\",\"description\":\"@RabbitListener count\"},{\"type\":\"RABBIT_LISTENER_ID\",\"description\":\"@RabbitListener createSupplierListener\"},{\"type\":\"RABBIT_LISTENER_ID\",\"description\":\"@RabbitListener getSuppliersListener\"},{\"type\":\"PARENT_POM_VERSION\",\"description\":\"Parent POM v. 5.6.47\"},{\"type\":\"SPRINGBOOT_VERSION\",\"description\":\"SpringBoot v. 2.4.2\"},{\"type\":\"LOGZ_URL\",\"description\":\"Logz URL\"},{\"type\":\"GIT_BRANCH\",\"description\":\"Git branch my-special-branch\"},{\"type\":\"myLabel\",\"description\":\"Label myLabel\"},{\"type\":\"foo\",\"description\":\"Label foo\"}]}}}"
                )
            )
    }

    @Test
    fun graphQlByType() {
        val query = GraphQlQuery(query = "{ search(query:\"$RABBIT_LISTENER_ID\") { facets { name, extraTags } } } ")

        given().`when`().body(query).post("/graphql")
            .then()
            .statusCode(200)
            .body(`is`("{\"data\":{\"search\":{\"facets\":[{\"name\":\"latte.queue.check-exists\",\"extraTags\":[\"infra:rabbitmq\",\"latte-service\"]},{\"name\":\"latte.queue.count\",\"extraTags\":[\"infra:rabbitmq\",\"latte-service\"]},{\"name\":\"latte.queue.create\",\"extraTags\":[\"infra:rabbitmq\",\"latte-service\"]},{\"name\":\"latte.queue.delete\",\"extraTags\":[\"infra:rabbitmq\",\"latte-service\"]},{\"name\":\"latte.queue.list\",\"extraTags\":[\"infra:rabbitmq\",\"latte-service\"]},{\"name\":\"latte.queue.read\",\"extraTags\":[\"infra:rabbitmq\",\"latte-service\"]},{\"name\":\"latte.queue.rename\",\"extraTags\":[\"infra:rabbitmq\",\"latte-service\"]},{\"name\":\"latte.queue.zip\",\"extraTags\":[\"infra:rabbitmq\",\"latte-service\"]},{\"name\":\"createSupplierListener\",\"extraTags\":[\"crunch-cappuccino-service\",\"infra:rabbitmq\"]},{\"name\":\"getSuppliersListener\",\"extraTags\":[\"crunch-cappuccino-service\",\"infra:rabbitmq\"]}]}}}"))
    }

    @Suppress("unused")
    private class GraphQlQuery(val param: String = "query", val query: String)
}
