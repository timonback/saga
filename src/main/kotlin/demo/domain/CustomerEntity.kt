package demo.domain

data class CustomerEntity(
    private val id: String,
) : BusinessEntity {
    override fun getId(): String {
        return id
    }
}