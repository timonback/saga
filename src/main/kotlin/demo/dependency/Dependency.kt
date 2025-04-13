package demo.dependency

import demo.domain.BusinessEntity
import demo.exception.DependencyHealthException
import demo.repository.DataStore

class Dependency(
    val name: String,
    var healthStatus: Health
) {
    val dataStore: DataStore<BusinessEntity> = DataStore()

    fun create(entity: BusinessEntity) {
        if (healthStatus in listOf(Health.DOWN)) {
            throw DependencyHealthException("Dependency $name failure mode=$healthStatus while creating")
        }

        require(!exists(entity)) { "Entity ${entity.getId()} already exists in dependency $name" }

        if (healthStatus in listOf(Health.CREATE_FAILS)) {
            throw DependencyHealthException("Dependency $name failure mode=$healthStatus while creating")
        }
        dataStore.put(entity)
        println("DEBUG: Created entity ${entity.getId()} in dependency $name")
    }

    fun exists(entity: BusinessEntity): Boolean {
        if (healthStatus in listOf(Health.DOWN)) {
            throw DependencyHealthException("Dependency $name failure mode=$healthStatus while checking existence")
        }

        return dataStore.get(entity) != null
    }

    fun remove(entity: BusinessEntity) {
        if (healthStatus in listOf(Health.DOWN)) {
            throw DependencyHealthException("Dependency $name failure mode=$healthStatus while removing")
        }

        require(exists(entity)) { "Entity ${entity.getId()} does not exist in dependency $name" }

        if (healthStatus in listOf(Health.REMOVE_FAILS)) {
            throw DependencyHealthException("Dependency $name failure mode=$healthStatus while removing")
        }
        dataStore.remove(entity)
        println("DEBUG: Removed entity ${entity.getId()} from dependency $name")
    }

    enum class Health {
        UP,
        CREATE_FAILS,
        REMOVE_FAILS,
        DOWN,
    }
}