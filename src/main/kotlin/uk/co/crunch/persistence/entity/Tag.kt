package uk.co.crunch.persistence.entity

import javax.persistence.Embeddable

@Embeddable
data class Tag(val name: String) : java.io.Serializable

object Tags {
    val RABBIT_TAG = Tag("infra:rabbitmq")
    val MYSQL_TAG = Tag("infra:mysql")
}
