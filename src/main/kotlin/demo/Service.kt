package demo

import demo.dependency.Dependency
import demo.domain.BusinessEntity
import demo.repository.DataStore
import demo.util.Result


class Service(
    private val dataStore: DataStore<Saga.ModellingState>,
    private val dep1: Dependency,
    private val dep2: Dependency,
    var rollbackMode: Saga.RollbackMode = Saga.RollbackMode.NONE,
    var failureStrategy: Saga.FailureStrategy = Saga.FailureStrategy.None,
) {
    fun create(entity: BusinessEntity): Result<Saga.State> {
        val saga = Saga(
            modelledEntities = dataStore,
            dep1 = dep1,
            dep2 = dep2,
            rollbackMode = rollbackMode,
            failureStrategy = failureStrategy
        )
        return saga.run(entity)
    }
}

