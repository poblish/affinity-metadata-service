package uk.co.crunch

import com.fasterxml.jackson.databind.ObjectMapper
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.base.OperationContext
import io.fabric8.kubernetes.client.informers.ResourceEventHandler
import io.quarkus.runtime.Startup
import org.jboss.logging.Logger
import uk.co.crunch.persistence.FactService
import uk.co.crunch.persistence.entity.Fact
import uk.co.crunch.persistence.entity.FactSource
import uk.co.crunch.persistence.entity.FactSource.APP_DEPLOYMENT
import uk.co.crunch.persistence.entity.FactSource.RABBIT_TOPOLOGY
import uk.co.crunch.persistence.entity.FactTypes
import uk.co.crunch.persistence.entity.Tag
import uk.co.crunch.persistence.entity.Tags
import uk.co.crunch.persistence.entity.Tags.RABBIT_TAG
import uk.co.crunch.platform.api.k8s.RbacRolePermission
import uk.co.crunch.platform.api.k8s.RbacRolePermissions
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@RbacRolePermissions(
    RbacRolePermission(resources = ["configmaps"], verbs = ["get", "list", "watch"]),
    RbacRolePermission(apiGroups = ["extensions", "apps"], resources = ["deployments"], verbs = ["get", "list", "watch"])
)
@Startup
@Singleton
class K8s(client: KubernetesClient, private val facts: FactService, private val objectMapper: ObjectMapper) {

    init {
        LOG.info("Starting K8s on $ENVIRONMENT...")

        val sharedInformerFactory = client.informers()
        val opsCtxt = OperationContext().withNamespace("default").withLabels(/* FIXME */ mapOf("component" to "backend"))

        val resyncPeriod = TimeUnit.MINUTES.toMillis(10)

        sharedInformerFactory.sharedIndexInformerFor(Deployment::class.java, opsCtxt, resyncPeriod).also { i ->
            i.addEventHandler(object : ResourceEventHandler<Deployment> {
                override fun onAdd(obj: Deployment?) {
                    obj?.let { onDeploymentEvent(it) }
                }

                override fun onUpdate(oldObj: Deployment?, newObj: Deployment?) {
                    newObj?.let { onDeploymentEvent(it) }
                }

                override fun onDelete(obj: Deployment?, deletedFinalStateUnknown: Boolean) {
                    obj?.let { onDeploymentEvent(it) }
                }
            })
        }

        sharedInformerFactory.sharedIndexInformerFor(ConfigMap::class.java, opsCtxt, resyncPeriod).also { i ->
            i.addEventHandler(object : ResourceEventHandler<ConfigMap> {
                override fun onAdd(obj: ConfigMap?) {
                    obj?.let { onConfigMap(it) }
                }

                override fun onUpdate(oldObj: ConfigMap?, newObj: ConfigMap?) {
                    newObj?.let { onConfigMap(it) }
                }

                override fun onDelete(obj: ConfigMap?, deletedFinalStateUnknown: Boolean) {
                    obj?.let { onConfigMap(it) }
                }
            })
        }

        sharedInformerFactory.startAllRegisteredInformers()

        LOG.info("Started K8s")
    }

    private fun onDeploymentEvent(deployment: Deployment) {
        deployment.metadata.let { md ->
            val service = md.labels[RELEASE]!!.toLowerCase()  // poss. more true than `app`

            if (LOG.isTraceEnabled) {
                LOG.trace("Got event for deployment [${md.name}], service [$service]")
            }

            facts.zeroFactSourceServiceTags(APP_DEPLOYMENT, service)

            // store "release" only, generate real URL at search time
            saveForTag(service, FactTypes.LOGZ_URL, APP_DEPLOYMENT, service) { "Logz URL" }

            md.annotations[BB_REPO]?.let { saveForTag(it, FactTypes.BB_REPO, APP_DEPLOYMENT, service) { "BitBucket repo $it" } }

            md.labels[STACK]?.let { saveForTag(it, FactTypes.STACK, APP_DEPLOYMENT, service, caseSensitive = false) { "$it stack" } }
            md.labels[OWNER]?.let { saveForTag(it, FactTypes.OWNER, APP_DEPLOYMENT, service, caseSensitive = false) { "Owned by $it" } }
            md.labels[PRODUCT]?.let { saveForTag(it, FactTypes.PRODUCT, APP_DEPLOYMENT, service, caseSensitive = false) { "Product: $it" } }
            md.labels[PARENT]?.let { saveForTag(it, FactTypes.PARENT_POM_VERSION, APP_DEPLOYMENT, service) { "Parent POM v. $it" } }
            md.labels[SB_LABEL]?.let {
                saveForTag(
                    it,
                    FactTypes.SPRINGBOOT_VERSION,
                    APP_DEPLOYMENT,
                    service
                ) { "SpringBoot v. $it" }
            }
            md.labels[VERSION]?.let {
                val branch = BRANCH_VERSION_REGEX.find(it)?.groups?.get(1)?.value ?: "master"
                saveForTag(branch, FactTypes.GIT_BRANCH, APP_DEPLOYMENT, service) { "Git branch $branch" }
            }
            md.labels[CLOUD_CONFIG_PROFILES_LABEL]?.takeIf { it != "none" }?.let {
                it.split("__").distinct().forEach { profile ->
                    saveForTag(profile, FactTypes.CLOUD_CONFIG_PROFILE, APP_DEPLOYMENT, service) { "$profile Cloud Config profile" }
                }
            }
            md.labels[MYSQL_SCHEMA_LABEL]?.takeIf { it != "none" }?.run {
                val tags = { initialTags(service).also { it.add(Tags.MYSQL_TAG) } }
                saveForTag(this, FactTypes.MYSQL_SCHEMA_NAME, APP_DEPLOYMENT, service, tags) { "Schema $this" }
            }

            filterWellKnownLabels(md.labels).forEach { (name, value) ->
                saveForTag(value, name, APP_DEPLOYMENT, service) { "Label $name" }
            }
        }
    }

    private fun onConfigMap(configMap: ConfigMap) {
        configMap.rabbitTopologies()?.let { handleRabbitTopology(it) }
    }

    private fun handleRabbitTopology(configMap: ConfigMap) {
        if (LOG.isTraceEnabled) {
            LOG.trace("Got event for CM [${configMap.metadata.name}]")
        }

        if (configMap.data.isEmpty()) {
            return
        }

        val firstAndOnlyMapping = configMap.data.entries.first()
        val topologyData = objectMapper.readValue(firstAndOnlyMapping.value, RabbitTopology::class.java)
        val service = configMap.metadata.labels[RELEASE]!!.toLowerCase()  // poss. more true than `app`

        facts.zeroFactSourceServiceTags(RABBIT_TOPOLOGY, service)

        if (LOG.isTraceEnabled) {
            LOG.trace("Got ${topologyData.listeners.size} listeners & ${topologyData.senders.size} senders for [$service]")
        }

        val exchangesList = (topologyData.senders.map { it.exchange } + topologyData.listeners.map { it.exchange }).distinct()
        exchangesList.forEach { saveRabbitExchange(it, service) }

        topologyData.listeners.map { it.queue }.forEach { saveRabbitQueue(it, service) }
        topologyData.listeners.map { it.id }.forEach { saveRabbitListener(it, service) }

        if (topologyData.listeners.isNotEmpty()) {
            saveForTag(topologyData.listeners.size.toString(), FactTypes.RABBIT_LISTENER_COUNT, RABBIT_TOPOLOGY, service,
                { initialRabbitTags(service) }) { "@RabbitListener count" }
        }
    }

    private fun filterWellKnownLabels(labels: Map<String, String>) = labels.filterKeys { it !in WELL_KNOWN_LABELS }

    private fun saveRabbitExchange(exchange: String, service: String) =
        saveForTag(exchange, FactTypes.RABBIT_EXCHANGE, RABBIT_TOPOLOGY, service, { initialRabbitTags(service) }) { "Exchange $exchange" }

    private fun saveRabbitQueue(queue: String, service: String) =
        saveForTag(queue, FactTypes.RABBIT_QUEUE, RABBIT_TOPOLOGY, service, { initialRabbitTags(service) }) { "Queue $queue" }

    private fun saveRabbitListener(id: String, service: String) =
        saveForTag(id, FactTypes.RABBIT_LISTENER_ID, RABBIT_TOPOLOGY, service, { initialRabbitTags(service) }) { "@RabbitListener $id" }

    private fun saveForTag(
        factName: String,
        factType: String,
        factSource: FactSource,
        serviceReleaseName: String,
        initialTags: () -> MutableSet<Tag> = { initialTags(serviceReleaseName) },
        caseSensitive: Boolean = true,
        descriptionGetter: () -> String
    ) {
        val nameFilter = if (caseSensitive) "name = '$factName'" else "LCASE(name) = '${factName.toLowerCase()}'"

        val newSynonyms = synonymSetForServiceName(serviceReleaseName)

        val fact = facts.query("FROM Fact WHERE type='$factType' AND $nameFilter")?.also {
            // Add new tags and synonyms for this service usage, see if that changes the entity...

            val tagAdded = it.tags.add(Tag(serviceReleaseName))
            val synAdded = it.synonyms.addAll(newSynonyms)

            if (!tagAdded && !synAdded) {
                if (LOG.isTraceEnabled) {
                    LOG.trace("Skip update of $it (syn = ${it.synonyms} for new tag $serviceReleaseName & syns $newSynonyms as no change ($tagAdded / $synAdded)")
                }
                return
            }
        } ?: run {
            Fact().apply {
                name = factName
                type = factType
                source = factSource
                ownerRelease = serviceReleaseName
                description = descriptionGetter()
                synonyms = newSynonyms
                tags = initialTags() // add routing keys? queue names?
            }
        }

        facts.saveFact(fact)
    }

    private fun initialTags(serviceName: String) = hashSetOf(Tag(serviceName))
    private fun initialRabbitTags(serviceName: String) = initialTags(serviceName).also { it.add(RABBIT_TAG) }

    private fun synonymSetForServiceName(serviceName: String) = synonymsForServiceName(serviceName).map { Tag(it) }.toMutableSet()

    private fun synonymsForServiceName(serviceName: String) = run {
        val serviceLess = serviceName.removeSuffix("-service")
        setOf(serviceName.removePrefix("crunch-"), serviceLess, serviceLess.removePrefix("crunch-"))
            .filter { it != serviceName }  // Don't duplicate the original
    }

    companion object {
        private val LOG: Logger = Logger.getLogger(K8s::class.java)

        val ENVIRONMENT = System.getenv("CRUNCH_ENVIRONMENT") ?: "Production"

        val BRANCH_VERSION_REGEX = "0.\\d+-(.*)".toRegex()

        private const val RELEASE = "release"
        private const val STACK = "stack"
        private const val OWNER = "owner"
        private const val PRODUCT = "product"
        private const val VERSION = "version"
        private const val BB_REPO = "origin-repository"
        private const val PARENT = "parentVersion"
        private const val SB_LABEL = "springBootVersion"
        private const val CLOUD_CONFIG_PROFILES_LABEL = "cloudConfigProfiles"
        private const val MYSQL_SCHEMA_LABEL = "mysqlSchemaName"

        private val WELL_KNOWN_LABELS = arrayOf(
            RELEASE, STACK, OWNER, PRODUCT, VERSION,
            PARENT, SB_LABEL,
            CLOUD_CONFIG_PROFILES_LABEL, MYSQL_SCHEMA_LABEL,
            "heritage", "component", "app", "chart", "pod-template-hash", "version",
            "app.kubernetes.io/managed-by", "goPipelineCounter"
        )
    }
}

// Match the generator from Maven plugin > RabbitUsageDetector
private data class RabbitTopology(val listeners: List<ListenerRecord>, val senders: Set<SenderRecord>)
private data class ListenerRecord(val id: String, val exchange: String, val queue: String, val routingKey: String)
private data class SenderRecord(val id: String, val exchange: String, val routingKey: String)

private fun ConfigMap.rabbitTopologies(): ConfigMap? {
    if (this.metadata.annotations["v1beta.k8s.crunch.co.uk/autoconfig-type"] == "rabbitmq/topology") {
        return this
    }
    return null
}
