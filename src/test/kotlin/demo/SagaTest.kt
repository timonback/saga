package demo

import demo.dependency.Dependency
import demo.domain.CustomerEntity
import demo.domain.Identifiable
import demo.repository.DataStore
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource


class SagaTest {
    private val dataStore: DataStore<Saga.ModellingState> = DataStore()
    private val dep1: Dependency = Dependency(name = "dep1", healthStatus = Dependency.Health.UP)
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
        result.getException().message shouldBe "Entity customer1 already exists in dependency dep1"
        entity shouldHaveState Saga.State.START_DEP1
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
        entity shouldHaveState Saga.State.START_DEP2
    }

    @Nested
    inner class Rollback {
        @Test
        fun `should attempt rollback when dependency 2 is down, but fail`() {
            // given
            val entity = CustomerEntity("customer1")
            dep2.healthStatus = Dependency.Health.DOWN
            service.rollbackMode = Saga.RollbackMode.ROLLBACK_ON_FAILURE

            // when
            val result = service.create(entity)

            // then
            result.getException().message shouldBe "Dependency dep2 failure mode=DOWN while checking existence"
            entity shouldHaveState Saga.State.START_DEP2
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

        @Test
        fun `should recover when create in dependency 2 is fails`() {
            // given
            val entity = CustomerEntity("customer1")
            dep2.healthStatus = Dependency.Health.CREATE_FAILS
            service.rollbackMode = Saga.RollbackMode.ROLLBACK_ON_FAILURE

            // when
            val result = service.create(entity)

            // then
            result.getValue() shouldBe Saga.State.INITIAL
        }

        @Test
        fun `should fail when trying to delete entity in dep 2 due to failure mode`() {
            // given
            val entity = CustomerEntity("customer1")
            dep2.create(entity)
            dep2.healthStatus = Dependency.Health.REMOVE_FAILS
            service.rollbackMode = Saga.RollbackMode.ROLLBACK_ON_FAILURE

            // when
            val result = service.create(entity)

            // then
            result.getException().message shouldBe "Dependency dep2 failure mode=REMOVE_FAILS while removing"
            entity shouldHaveState Saga.State.START_DEP2
        }
    }

    @Nested
    inner class Failure {
        @ParameterizedTest
        @CsvSource("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
        fun `should eventually succeed for failure`(invocation: Int) {
            // given
            val entity = CustomerEntity("customer1")
            service.rollbackMode = Saga.RollbackMode.ROLLBACK_ON_FAILURE
            service.failureStrategy = Saga.FailureStrategy.OnInvocation(listOf(invocation))

            // when
            repeat(10) { kotlin.runCatching { service.create(entity) } }
            val result = service.create(entity)

            // then
            result.getValue() shouldBe Saga.State.CREATED_OR_UPDATED
        }

        @ParameterizedTest
        @CsvSource("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
        fun `should eventually succeed for repeated failures`(maxInvocation: Int) {
            // given
            val entity = CustomerEntity("customer1")
            service.failureStrategy = Saga.FailureStrategy.OnInvocation((0..maxInvocation).toList())

            // when
            repeat(10) { kotlin.runCatching { service.create(entity) } }
            val result = service.create(entity)

            // then
            result.getValue() shouldBe Saga.State.CREATED_OR_UPDATED
        }
    }

    companion object {
        @JvmStatic
        fun states() = Saga.State.entries
    }


    infix fun Identifiable.shouldHaveState(state: Saga.State) {
        dataStore.get(this)?.getState() shouldBe state
    }
}
