package demo.dependency

import demo.domain.BusinessEntity
import demo.repository.DataStore

class Dependency(
    val name: String,
    var healthStatus: Health
) {
    val dataStore: DataStore<BusinessEntity> = DataStore()

    fun create(entity: BusinessEntity) {
        if (healthStatus in listOf(Health.DOWN, Health.CREATE_FAILS)) {
            throw Exception("Dependency $name failure mode=$healthStatus while creating")
        }

        require(!exists(entity)) { "Entity already exists in dependency $name" }

        dataStore.put(entity)
    }

    fun exists(entity: BusinessEntity): Boolean {
        if (healthStatus in listOf(Health.DOWN)) {
            throw Exception("Dependency $name failure mode=$healthStatus while checking existence")
        }

        return dataStore.get(entity) != null
    }

    fun remove(entity: BusinessEntity) {
        if (healthStatus in listOf(Health.DOWN)) {
            throw Exception("Dependency $name failure mode=$healthStatus while removing")
        }

        dataStore.remove(entity)
    }

    enum class Health {
        UP,
        CREATE_FAILS,
        // TODO: REMOVE_FAILS
        DOWN,
    }
}