package demo

import demo.dependency.Dependency
import demo.exception.DependencyHealthException
import demo.domain.BusinessEntity
import demo.domain.Identifiable
import demo.repository.DataStore
import demo.util.Result
import demo.util.Result.Failure


class Saga(
    val modelledEntities: DataStore<ModellingState>,
    val dep1: Dependency,
    val dep2: Dependency,
    val rollbackMode: RollbackMode,
    val failureStrategy: FailureStrategy
) {

    fun run(entity: BusinessEntity): Result<State> {
        val modellingEntity = modelledEntities.get(entity) //
            ?: ModellingStateImpl(entity, State.INITIAL).also { modelledEntities.put(it) }

        return modellingEntity
            .apply { executeInitialRollback(this) }
            .run {
                executeCreation(this)
                    .tryRecovery { exception -> executeRecoveryRollback(this, exception) }
                    .map { this.getState() }
            }
    }

    private fun executeInitialRollback(modellingEntity: ModellingState) {
        val rollbackEnabled = rollbackMode != RollbackMode.NONE
        val needsRollback = modellingEntity.getState() != State.INITIAL
        if (rollbackEnabled && needsRollback) {
            executeCreationRollback(modellingEntity)
        }
    }

    private fun executeRecoveryRollback(modellingEntity: ModellingState, exception: Throwable): State {
        val rollbackEnabled = rollbackMode == RollbackMode.ROLLBACK_ON_FAILURE
        return if (rollbackEnabled) {
            executeCreationRollback(modellingEntity).getValueOrThrow()
        } else {
            throw exception
        }
    }

    /**
     * This is a bit tricky, since the action might have been executed, but the internal state update was lost
     * Therefore, the cleanup steps are shifted to the previous state
     * Those are required to check if the action had been executed to clean it up
     */
    private fun executeCreationRollback(modellingEntity: ModellingState): Result<State> {
        println("DEBUG: Running rollback logic")
        return runInStateMachine {

            val newState = when (modellingEntity.getState()) {
                State.CREATED_OR_UPDATED -> State.FINISH_DEP2
                State.FINISH_DEP2 -> State.START_DEP2

                State.START_DEP2 -> {
                    absorbNonFatalException {
                        if (dep2.exists(modellingEntity)) dep2.remove(modellingEntity)
                    }
                    State.INTERNAL_MODELLING1
                }

                State.INTERNAL_MODELLING1 -> State.FINISH_DEP1
                State.FINISH_DEP1 -> State.START_DEP1

                State.START_DEP1 -> {
                    absorbNonFatalException {
                        if (dep1.exists(modellingEntity)) dep1.remove(modellingEntity)
                    }
                    State.INITIAL
                }

                State.INITIAL -> State.INITIAL
            }

            newState
                .also {
                    if (failureStrategy.isFailure()) throw Exception("Simulated failure (service shutdown)")

                    modellingEntity.updateState(it)
                }
        }
    }

    /**
     * Ignore non-fatal exception, like entities not existing (since not being created),
     * but fail for health errors, indicating that an assumption cannot be verified
     */
    private fun absorbNonFatalException(action: () -> Unit) {
        val result = kotlin.runCatching { action() }
        if (result.exceptionOrNull() is DependencyHealthException) throw result.exceptionOrNull()!!
    }

    private fun executeCreation(modellingEntity: ModellingState): Result<State> {
        println("DEBUG: Running creating logic")
        return runInStateMachine {
            val newState = when (modellingEntity.getState()) {
                State.INITIAL -> State.START_DEP1
                State.START_DEP1 -> {
                    dep1.create(modellingEntity)
                    State.FINISH_DEP1
                }

                State.FINISH_DEP1 -> State.INTERNAL_MODELLING1
                State.INTERNAL_MODELLING1 -> State.START_DEP2
                State.START_DEP2 -> {
                    dep2.create(modellingEntity)
                    State.FINISH_DEP2
                }

                State.FINISH_DEP2 -> State.CREATED_OR_UPDATED
                State.CREATED_OR_UPDATED -> State.CREATED_OR_UPDATED
            }

            newState
                .also {
                    if (failureStrategy.isFailure()) throw Exception("Simulated failure (service shutdown)")

                    modellingEntity.updateState(it)
                }
        }
    }

    private fun <R : Any> runInStateMachine(action: () -> R): Result<R> {
        lateinit var lastResult: R

        for (i in 0..State.entries.size * 2) {
            val execution = kotlin.runCatching {
                lastResult = action()
            }

            if (execution.isFailure) {
                return Failure(exception = execution.exceptionOrNull()!!)
            }
        }

        return Result.Success(lastResult)
    }

    enum class State {
        /**
         * Identical to deleted
         */
        INITIAL,
        START_DEP1,
        FINISH_DEP1,
        INTERNAL_MODELLING1,
        START_DEP2,
        FINISH_DEP2,
        CREATED_OR_UPDATED,
    }

    interface ModellingState : BusinessEntity {
        fun getState(): State

        /**
         * Assumption, this is flushed to disk/db
         */
        fun updateState(state: State)
    }

    class ModellingStateImpl(
        private val id: Identifiable,
        private var currentState: State
    ) : ModellingState {
        override fun getId(): String = id.getId()
        override fun getState(): State = currentState
        override fun updateState(state: State) {
            if (getState() != state) {
                println("DEBUG: State changed from $currentState to $state")
            }
            this.currentState = state
        }
    }

    enum class RollbackMode {
        NONE,
        ROLLBACK_ON_FAILURE,
    }

    /**
     * Purely for testing failure scenarios
     */
    sealed class FailureStrategy {
        data object None : FailureStrategy() {
            override fun internalIsFailure(): Boolean = false
        }

        data class OnInvocation(val invocations: List<Int>) : FailureStrategy() {
            private var invocationCount = 0

            override fun internalIsFailure(): Boolean {
                return invocationCount++ in invocations
            }
        }

        internal abstract fun internalIsFailure(): Boolean
        fun isFailure(): Boolean {
            return internalIsFailure()
                // .also { println("DEBUG: Injecting failure: $it") }
                .also { if (it) println("DEBUG: Injecting failure") }
        }
    }
}
