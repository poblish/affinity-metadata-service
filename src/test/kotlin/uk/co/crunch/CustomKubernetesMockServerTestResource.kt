package uk.co.crunch

import io.fabric8.kubernetes.api.model.ConfigMapBuilder
import io.fabric8.kubernetes.api.model.ConfigMapListBuilder
import io.fabric8.kubernetes.api.model.ListMetaBuilder
import io.fabric8.kubernetes.api.model.WatchEvent
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder
import io.fabric8.kubernetes.api.model.apps.DeploymentListBuilder
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer
import io.quarkus.test.kubernetes.client.KubernetesMockServerTestResource

@Suppress("DEPRECATION")  // Can't work out how to fix KubernetesMockServerTestResource change from 1.12.1 - docs misleading
class CustomKubernetesMockServerTestResource : KubernetesMockServerTestResource() {
    override fun configureMockServer(mockServer: KubernetesMockServer) {

        val latteCM = ConfigMapBuilder().withNewMetadata()
            .withNamespace("default")
            .withName("latte-service-rabbitmq-topology")
            .withLabels(mapOf("release" to "latte-service", "component" to "backend"))
            .withAnnotations(mapOf("v1beta.k8s.crunch.co.uk/autoconfig-type" to "rabbitmq/topology"))
            .withResourceVersion("1")
            .endMetadata()
            .withData(
                mapOf(
                    "latte-service-topology.json" to "{\"listeners\":[{\"id\":\"latte.queue.check-exists\",\"exchange\":\"latte\",\"queue\":\"consumer.latte.producer.latte.check-exists\",\"routingKey\":\"latte.check-exists\"},{\"id\":\"latte.queue.count\",\"exchange\":\"latte\",\"queue\":\"consumer.latte.producer.latte.count\",\"routingKey\":\"latte.count\"},{\"id\":\"latte.queue.create\",\"exchange\":\"latte\",\"queue\":\"consumer.latte.producer.latte.create\",\"routingKey\":\"latte.create\"},{\"id\":\"latte.queue.delete\",\"exchange\":\"latte\",\"queue\":\"consumer.latte.producer.latte.delete\",\"routingKey\":\"latte.delete\"},{\"id\":\"latte.queue.list\",\"exchange\":\"latte\",\"queue\":\"consumer.latte.producer.latte.list\",\"routingKey\":\"latte.list\"},{\"id\":\"latte.queue.read\",\"exchange\":\"latte\",\"queue\":\"consumer.latte.producer.latte.read\",\"routingKey\":\"latte.read\"},{\"id\":\"latte.queue.rename\",\"exchange\":\"latte\",\"queue\":\"consumer.latte.producer.latte.rename\",\"routingKey\":\"latte.rename\"},{\"id\":\"latte.queue.zip\",\"exchange\":\"latte\",\"queue\":\"consumer.latte.producer.latte.zip\",\"routingKey\":\"latte.zip\"}],\"senders\":[]}"
                )
            )
            .build()

        val cappuccinoCM = ConfigMapBuilder().withNewMetadata()
            .withNamespace("default")
            .withName("crunch-cappuccino-service-rabbitmq-topology")
            .withLabels(mapOf("release" to "crunch-cappuccino-service", "component" to "backend"))
            .withAnnotations(mapOf("v1beta.k8s.crunch.co.uk/autoconfig-type" to "rabbitmq/topology"))
            .withResourceVersion("1")
            .endMetadata()
            .withData(
                mapOf(
                    "crunch-cappuccino-service-topology.json" to "{\"listeners\":[{\"id\":\"junk1\",\"exchange\":\"junkExc\",\"queue\":\"junkQ\",\"routingKey\":\"junkKey\"},{\"id\":\"createSupplierListener\",\"exchange\":\"cappuccino\",\"queue\":\"supplier.create\",\"routingKey\":\"supplier.create\"},{\"id\":\"getSuppliersListener\",\"exchange\":\"cappuccino\",\"queue\":\"suppliers.get\",\"routingKey\":\"suppliers.get\"}],\"senders\":[{\"id\":\"getDrinkAccountById\",\"exchange\":\"americano\",\"routingKey\":\"get.drink.account\"},{\"id\":\"getDrinkAccounts\",\"exchange\":\"americano\",\"routingKey\":\"get.drink.accounts\"},{\"id\":\"getCreditCardById\",\"exchange\":\"americano\",\"routingKey\":\"get.credit.card\"},{\"id\":\"getCreditCards\",\"exchange\":\"americano\",\"routingKey\":\"get.credit.cards\"},{\"id\":\"createDocument\",\"exchange\":\"latte\",\"routingKey\":\"latte.create\"},{\"id\":\"deleteDocument\",\"exchange\":\"latte\",\"routingKey\":\"latte.delete\"},{\"id\":\"listDocuments\",\"exchange\":\"latte\",\"routingKey\":\"latte.list\"},{\"id\":\"readDocument\",\"exchange\":\"latte\",\"routingKey\":\"latte.read\"},{\"id\":\"getTimedSecurityContext\",\"exchange\":\"tea\",\"routingKey\":\"public-api.session.validate-request\"},{\"id\":\"getTimedSecurityContext\",\"exchange\":\"tea\",\"routingKey\":\"abc\"}]}"
                )
            )
            .build()

        val cappuccinoCMWithListenerRemoved = ConfigMapBuilder().withNewMetadata()
            .withNamespace("default")
            .withName("crunch-cappuccino-service-rabbitmq-topology")
            .withLabels(mapOf("release" to "crunch-cappuccino-service", "component" to "backend"))
            .withAnnotations(mapOf("v1beta.k8s.crunch.co.uk/autoconfig-type" to "rabbitmq/topology"))
            .withResourceVersion("1")
            .endMetadata()
            .withData(
                mapOf(
                    "crunch-cappuccino-service-topology.json" to "{\"listeners\":[{\"id\":\"createSupplierListener\",\"exchange\":\"cappuccino\",\"queue\":\"supplier.create\",\"routingKey\":\"supplier.create\"},{\"id\":\"getSuppliersListener\",\"exchange\":\"cappuccino\",\"queue\":\"suppliers.get\",\"routingKey\":\"suppliers.get\"}],\"senders\":[{\"id\":\"getDrinkAccountById\",\"exchange\":\"americano\",\"routingKey\":\"get.drink.account\"},{\"id\":\"getDrinkAccounts\",\"exchange\":\"americano\",\"routingKey\":\"get.drink.accounts\"},{\"id\":\"getCreditCardById\",\"exchange\":\"americano\",\"routingKey\":\"get.credit.card\"},{\"id\":\"getCreditCards\",\"exchange\":\"americano\",\"routingKey\":\"get.credit.cards\"},{\"id\":\"createDocument\",\"exchange\":\"latte\",\"routingKey\":\"latte.create\"},{\"id\":\"deleteDocument\",\"exchange\":\"latte\",\"routingKey\":\"latte.delete\"},{\"id\":\"listDocuments\",\"exchange\":\"latte\",\"routingKey\":\"latte.list\"},{\"id\":\"readDocument\",\"exchange\":\"latte\",\"routingKey\":\"latte.read\"},{\"id\":\"getTimedSecurityContext\",\"exchange\":\"tea\",\"routingKey\":\"public-api.session.validate-request\"},{\"id\":\"getTimedSecurityContext\",\"exchange\":\"tea\",\"routingKey\":\"abc\"}]}"
                )
            )
            .build()

        val labelSelector = "labelSelector=component%3Dbackend"

        // Return initial empty CM list
        mockServer.expect().get().withPath("/api/v1/namespaces/default/configmaps?$labelSelector")
            .andReturn(
                200, ConfigMapListBuilder().withMetadata(ListMetaBuilder().build()).withItems(
                    listOf(latteCM)
                ).build()
            ).once()

        // Respond with a discovered CMs
        mockServer.expect().get().withPath("/api/v1/namespaces/default/configmaps?$labelSelector&watch=true")
            .andUpgradeToWebSocket().open(  // this syntax works but can cause MockWebserver errors
                WatchEvent(cappuccinoCM, "ADDED"),
                WatchEvent(cappuccinoCMWithListenerRemoved, "MODIFIED")
            ).done()
            .once()

        ////////////////////////////////////////////////

        val deployment = DeploymentBuilder()
            .withNewMetadata()
            .withName("crunch-cappuccino-service-backend")
            .withAnnotations(mapOf("origin-repository" to "https://bitbucket.org/crunch-ondemand/crunch-cappuccino-service"))
            .withLabels(
                mapOf(
                    "app" to "crunch-cappuccino-service",
                    "owner" to "MyTeam",
                    "product" to "Drinks",
                    "stack" to "Consumables",
                    "parentVersion" to "5.6.47",
                    "springBootVersion" to "2.4.2",
                    "release" to "crunch-cappuccino-service",
                    "version" to "0.1614940105-my-special-branch",
                    "cloudConfigProfiles" to "americano-api__public-api",
                    "mysqlSchemaName" to "mydb",
                    "myLabel" to "myLabelValue",
                    "foo" to "bar"
                )
            )
            .endMetadata()
            .withNewSpec()
            .withNewTemplate()
            .withNewSpec()
            .addNewContainer()
            .withName("crunch-cappuccino-service")
            .withImage("-")
            .endContainer()
            .endSpec()
            .endTemplate()
            .endSpec()
            .build()

        // Return initial empty deployments list
        mockServer.expect().get().withPath("/apis/apps/v1/namespaces/default/deployments?$labelSelector")
            .andReturn(
                200, DeploymentListBuilder().withMetadata(ListMetaBuilder().build()).withItems(
                    listOf()
                ).build()
            ).once()

        // Respond with a discovered deployment
        mockServer.expect().get()
            .withPath("/apis/apps/v1/namespaces/default/deployments?$labelSelector&watch=true")
            .andUpgradeToWebSocket().open()
            .immediately().andEmit(WatchEvent(deployment, "ADDED")).done()
            .once()
    }
}
