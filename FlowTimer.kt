import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

sealed class TimerResult {
    class OnTick(val timeLeft: Long?): TimerResult()
    object OnStop: TimerResult()
    object OnContinue: TimerResult()
    class OnPause(val remainingTimeMillis: Long): TimerResult()
    class Error(val error: Exception): TimerResult()
}

private enum class TimerState {
    RUNNING, PAUSED, STOPPED
}

enum class TimerErrorTypes(val message: String) {
    ALREADY_RUNNING("This instance of the timer is already running, create a new instance or stop your current timer"),
    CURRENTLY_PAUSED("This timer is currently paused. Choose to continue or stop to start over"),
    NO_TIMER_RUNNING("Trying to stop or pause a timer that isn't running")
}

private class TimerException(val type: TimerErrorTypes): Exception(type.message)

class FlowTimer {

    private var state: TimerState = TimerState.STOPPED
    val isRunning = state == TimerState.RUNNING

    fun start(countDownTimeSeconds: Long, delayMillis: Long = 1000): Flow<TimerResult> =
        flow {
            when (state) {
                TimerState.RUNNING -> {
                    emit(TimerResult.Error(TimerException(TimerErrorTypes.ALREADY_RUNNING)))
                }
                TimerState.PAUSED -> {
                    emit(TimerResult.Error(TimerException(TimerErrorTypes.CURRENTLY_PAUSED)))
                }
                else -> beginCountdown(countDownTimeSeconds, delayMillis).collect { emit(it) }
            }
        }


    fun stop(): Flow<TimerResult> =
        flow {
            if (state == TimerState.STOPPED) {
                emit(TimerResult.Error(TimerException(TimerErrorTypes.NO_TIMER_RUNNING)))
            } else {
                emit(TimerResult.OnStop)
            }
            state = TimerState.STOPPED
        }

    fun pause() {
        if (state == TimerState.PAUSED) {
            Log.e(TAG, "Already paused, check code for multiple callers")
        }
        state = TimerState.PAUSED
    }

    fun `continue`(): Flow<TimerResult> =
        flow {
            if (state == TimerState.RUNNING) {
                Log.e(TAG, "Already running, check code for multiple callers")
            }
            state = TimerState.RUNNING
            emit(TimerResult.OnContinue)
        }

    private fun beginCountdown(countDownTimeMillis: Long, delayMillis: Long = 1000): Flow<TimerResult> =
        flow {
            state = TimerState.RUNNING
            var timeLeft = countDownTimeMillis

            timerLoop@ while (true) {
                when {
                    timeLeft < 1 -> {
                        state = TimerState.STOPPED
                        this.emit(TimerResult.OnStop)
                        break@timerLoop
                    }
                    timeLeft > 0 && state == TimerState.RUNNING -> {
                        this.emit(TimerResult.OnTick(timeLeft))
                        delay(delayMillis)
                        timeLeft -= delayMillis
                    }
                    state == TimerState.PAUSED -> {
                        this.emit(TimerResult.OnPause(timeLeft))
                    }
                    state == TimerState.STOPPED -> {
                        this.emit(TimerResult.OnStop)
                        break@timerLoop
                    }
                }
            }
        }

    companion object {
        const val TAG = "FlowTimer"
    }
}
