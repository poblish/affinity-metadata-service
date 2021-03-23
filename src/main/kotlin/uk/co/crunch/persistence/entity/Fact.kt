package uk.co.crunch.persistence.entity

import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime
import javax.persistence.*

@Entity
class Fact(@Id @GeneratedValue(strategy = GenerationType.SEQUENCE) val id: Long? = null) : java.io.Serializable {

    @Column(nullable = false)
    lateinit var name: String

    @Column(nullable = false)
    lateinit var type: String

    @Column(nullable = false)
    lateinit var ownerRelease: String

    @Enumerated(EnumType.STRING)
    lateinit var source: FactSource

    @Column(nullable = false)
    lateinit var description: String

    // EAGER to simplify this reactive solution, and we're in-mem so not so bothered about data size / query perf
    @ElementCollection(fetch = FetchType.EAGER)
    @OrderBy("name")
    lateinit var tags: MutableSet<Tag>

    // EAGER to simplify this reactive solution, and we're in-mem so not so bothered about data size / query perf
    @ElementCollection(fetch = FetchType.EAGER)
    lateinit var synonyms: MutableSet<Tag>

    @UpdateTimestamp
    lateinit var updatedDateTime: LocalDateTime

    override fun toString(): String {
        return "Fact(id=$id, tags=$tags, name='$name', type=$type, source=$source, description='$description')"
    }
}
