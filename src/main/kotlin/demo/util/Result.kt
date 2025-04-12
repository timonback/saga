package demo.util


sealed class Result<T> {
    data class Success<T>(internal val value: T) : Result<T>()
    data class Failure<T>(internal val exception: Throwable) : Result<T>()

    fun getValue(): T {
        return when (this) {
            is Success -> value
            is Failure -> throw Exception("Result is a failure", exception)
        }
    }

    fun getException(): Throwable {
        return when (this) {
            is Success -> throw Exception("Result is a success")
            is Failure -> exception
        }
    }

    fun map(transform: (T) -> T): Result<T> {
        return when (this) {
            is Success -> Success(transform(value))
            is Failure -> this
        }
    }

    fun mapFailure(transform: (Throwable) -> Throwable): Result<T> {
        return when (this) {
            is Success -> this
            is Failure -> Failure(transform(exception))
        }
    }

    fun tryRecovery(recover: (Throwable) -> T): Result<T> {
        return when (this) {
            is Success -> this
            is Failure -> {
                try {
                    val value = recover(this.exception)
                    Success(value)
                } catch (e: Throwable) {
                    Failure(e)
                }
            }
        }
    }
}