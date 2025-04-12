package demo

import demo.dependency.Dependency
import demo.domain.CustomerEntity
import demo.repository.DataStore
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource


class SagaTest {
    private val dataStore: DataStore<Saga.ModellingState> = DataStore()
    private val dep1: Dependency = Dependency(name = "dep1", healthStatus= Dependency.Health.UP)
    private val dep2: Dependency = Dependency(name = "dep2", healthStatus = Dependency.Health.UP)
    private lateinit var service: Service

    @BeforeEach
    fun setUp() {
        dep1.dataStore.clear()
        dep2.dataStore.clear()
        dataStore.clear()

        service = Service(dataStore = dataStore, dep1 = dep1, dep2 = dep2)

        dep1.healthStatus = Dependency.Health.UP
        dep2.healthStatus = Dependency.Health.UP
    }

    @Test
    fun `should finish in state CREATED_OR_UPDATED`() {
        // given
        val entity = CustomerEntity("customer1")

        // when
        val result = service.create(entity)

        // then
        result.getValue() shouldBe Saga.State.CREATED_OR_UPDATED
    }

    @Test
    fun `should fail when re-creating the same entity in dep 1`() {
        // given
        val entity = CustomerEntity("customer1")
        dep1.create(entity)

        // when
        val result = service.create(entity)

        // then
        result.getException().message shouldBe "Entity already exists in dependency dep1"
    }

    @ParameterizedTest
    @MethodSource("states")
    fun `should finish modelling from any starting state`(state: Saga.State) {
        // given
        val entity = CustomerEntity("customer1")

        dataStore.put(Saga.ModellingStateImpl(entity, state))

        // when
        val result = service.create(entity)

        // then
        result.getValue() shouldBe Saga.State.CREATED_OR_UPDATED
    }

    @Test
    fun `should fail when dependency 2 is down`() {
        // given
        val entity = CustomerEntity("customer1")
        dep2.healthStatus = Dependency.Health.DOWN

        // when
        val result = service.create(entity)

        // then
        result.getException().message shouldBe "Dependency dep2 failure mode=DOWN while creating"
    }

    @Nested
    inner class Rollback {
        @Test
        fun `should rollback when dependency 2 is down`() {
            // given
            val entity = CustomerEntity("customer1")
            dep2.healthStatus = Dependency.Health.DOWN
            service.rollbackMode = Saga.RollbackMode.ROLLBACK_ON_FAILURE

            // when
            val result = service.create(entity)

            // then
            result.getValue() shouldBe Saga.State.INITIAL
        }

        @Test
        fun `should recover from previous failure in dep 2`() {
            // given
            val entity = CustomerEntity("customer1")

            service.rollbackMode = Saga.RollbackMode.NONE
            dep2.healthStatus = Dependency.Health.DOWN
            val setupResult = service.create(entity)
            setupResult.getException().message shouldBe "Dependency dep2 failure mode=DOWN while creating"

            // when
            service.rollbackMode = Saga.RollbackMode.ROLLBACK_ON_FAILURE
            dep2.healthStatus = Dependency.Health.UP
            val result = service.create(entity)

            // then
            result.getValue() shouldBe Saga.State.CREATED_OR_UPDATED
        }
    }

    companion object {
        @JvmStatic
        fun states() = Saga.State.entries
    }
}