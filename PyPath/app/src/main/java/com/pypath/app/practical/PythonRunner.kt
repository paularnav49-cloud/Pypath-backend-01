package com.pypath.app.practical

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock

/** How a run ended. */
sealed interface RunOutcome {
    /** Python finished on its own. [status] is the runner's verdict: "ok", "error", "exit:N" or "internal:…". */
    data class Completed(val status: String) : RunOutcome
    data object StoppedByUser : RunOutcome
    data class TimedOut(val limitMs: Long) : RunOutcome
    /** The Python process died (e.g. out of memory or a native crash). */
    data object Crashed : RunOutcome
}

interface RunnerListener {
    fun onEngineState(state: EngineState)
    fun onStarted()
    fun onOutput(stderr: Boolean, text: String)
    fun onInputRequested(prompt: String)
    fun onFinished(outcome: RunOutcome, elapsedMs: Long)
}

sealed interface EngineState {
    data object Starting : EngineState
    data class Ready(val pythonVersion: String) : EngineState
    data class Failed(val message: String) : EngineState
}

/**
 * UI-process client of [PythonRunnerService]. All callbacks arrive on the main thread.
 *
 * Execution limit: [timeLimitMs] of running time. Time spent waiting for the learner to answer
 * `input()` does not count. When the limit is hit (or Stop is pressed) the Python process is
 * killed — the only way to reliably interrupt arbitrary code such as `while True: pass` — and a
 * fresh interpreter process is started for the next run.
 */
class PythonRunner(
    context: Context,
    private val listener: RunnerListener,
    val timeLimitMs: Long = DEFAULT_TIME_LIMIT_MS,
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val incoming = Messenger(Handler(Looper.getMainLooper()) { handle(it); true })

    private var service: Messenger? = null
    private var pid = 0
    private var bound = false
    private var released = false
    var engine: EngineState = EngineState.Starting
        private set

    // current run
    private var pendingCode: String? = null
    private var running = false
    private var waitingForInput = false
    private var activeMs = 0L          // running time excluding input waits
    private var segmentStart = 0L      // when the current non-waiting segment began
    private var killReason: RunOutcome? = null

    /** A fresh connection object per bind, so callbacks from a dead binding are ignored. */
    private var connection: ServiceConnection? = null

    private fun newConnection() = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (connection !== this) return
            service = Messenger(binder)
            send(Message.obtain(null, RunnerProtocol.MSG_REGISTER).apply { replyTo = incoming })
        }

        override fun onServiceDisconnected(name: ComponentName?) { if (connection === this) processDied() }
        override fun onBindingDied(name: ComponentName?) { if (connection === this) processDied() }
    }

    private fun unbind() {
        connection?.let { try { app.unbindService(it) } catch (_: IllegalArgumentException) {} }
        connection = null
        bound = false
    }

    fun start() {
        if (bound || released) return
        setEngine(EngineState.Starting)
        val conn = newConnection()
        connection = conn
        bound = app.bindService(Intent(app, PythonRunnerService::class.java), conn, Context.BIND_AUTO_CREATE)
        if (!bound) setEngine(EngineState.Failed("Could not start the Python service"))
    }

    val isRunning get() = running

    /** Runs [code]. If the interpreter is still starting, the run begins as soon as it is ready. */
    fun run(code: String) {
        if (running) return
        (engine as? EngineState.Failed)?.let { listener.onFinished(RunOutcome.Completed("internal:" + it.message), 0); return }
        running = true
        waitingForInput = false
        killReason = null
        activeMs = 0
        segmentStart = SystemClock.elapsedRealtime()
        if (engine is EngineState.Ready && service != null) sendRun(code) else {
            pendingCode = code
            if (!bound) start()
        }
    }

    fun sendInput(text: String) {
        if (!running || !waitingForInput) return
        waitingForInput = false
        segmentStart = SystemClock.elapsedRealtime()
        send(Message.obtain(null, RunnerProtocol.MSG_INPUT).apply { data = Bundle().apply { putString(RunnerProtocol.KEY_TEXT, text) } })
    }

    fun stop() {
        if (!running) return
        if (pendingCode != null) { // never reached Python
            pendingCode = null
            finish(RunOutcome.StoppedByUser)
            return
        }
        kill(RunOutcome.StoppedByUser)
    }

    /** Elapsed running time of the current run (excluding input waits). */
    fun elapsedMs(): Long = if (!running) activeMs else activeMs + if (waitingForInput) 0 else SystemClock.elapsedRealtime() - segmentStart

    fun release() {
        released = true
        main.removeCallbacks(watchdog)
        unbind()
        if (pid != 0 && pid != Process.myPid()) Process.killProcess(pid)
        service = null
    }

    // ───────────────────────── internals ─────────────────────────

    private var lastCode: String? = null

    private fun sendRun(code: String) {
        pendingCode = null
        lastCode = code
        segmentStart = SystemClock.elapsedRealtime()
        send(Message.obtain(null, RunnerProtocol.MSG_RUN).apply { data = Bundle().apply { putString(RunnerProtocol.KEY_CODE, code) } })
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, WATCHDOG_TICK_MS)
    }

    private val watchdog = object : Runnable {
        override fun run() {
            if (!running || killReason != null) return
            if (elapsedMs() >= timeLimitMs) kill(RunOutcome.TimedOut(timeLimitMs))
            else main.postDelayed(this, WATCHDOG_TICK_MS)
        }
    }

    private fun kill(reason: RunOutcome) {
        if (killReason != null) return
        if (!waitingForInput) activeMs += SystemClock.elapsedRealtime() - segmentStart
        waitingForInput = false
        killReason = reason
        main.removeCallbacks(watchdog)
        if (pid != 0 && pid != Process.myPid()) Process.killProcess(pid)
        // The disconnect callback completes the run and restarts the interpreter. Fallback in
        // case it is delayed, so the UI never stays stuck in "running".
        main.postDelayed({ if (running && killReason === reason) { finish(reason); restartEngine() } }, 1500)
    }

    private fun processDied() {
        service = null
        pid = 0
        val reason = killReason
        if (running) {
            if (pendingCode == null) finish(reason ?: RunOutcome.Crashed)
        }
        restartEngine()
    }

    private fun restartEngine() {
        if (released) return
        unbind()
        service = null
        start()
    }

    private fun finish(outcome: RunOutcome, elapsedOverride: Long? = null) {
        if (!running) return
        if (!waitingForInput && killReason == null) activeMs += SystemClock.elapsedRealtime() - segmentStart
        running = false
        waitingForInput = false
        killReason = null
        main.removeCallbacks(watchdog)
        listener.onFinished(outcome, elapsedOverride ?: activeMs)
    }

    private fun handle(msg: Message) {
        when (msg.what) {
            RunnerProtocol.MSG_READY -> {
                pid = msg.arg1
                setEngine(EngineState.Ready(msg.data.getString(RunnerProtocol.KEY_TEXT).orEmpty()))
                pendingCode?.let { if (running) sendRun(it) }
            }
            RunnerProtocol.MSG_INIT_FAILED -> {
                val err = msg.data.getString(RunnerProtocol.KEY_TEXT).orEmpty()
                setEngine(EngineState.Failed(err))
                if (running) { pendingCode = null; finish(RunOutcome.Completed("internal:$err")) }
            }
            RunnerProtocol.MSG_STARTED -> if (running && killReason == null) {
                segmentStart = SystemClock.elapsedRealtime()
                listener.onStarted()
            }
            RunnerProtocol.MSG_OUTPUT -> if (running) {
                val kinds = msg.data.getIntArray(RunnerProtocol.KEY_KINDS) ?: return
                val texts = msg.data.getStringArrayList(RunnerProtocol.KEY_TEXTS) ?: return
                kinds.forEachIndexed { i, k -> listener.onOutput(k == RunnerProtocol.KIND_STDERR, texts[i]) }
            }
            RunnerProtocol.MSG_INPUT_REQUEST -> if (running && killReason == null) {
                activeMs += SystemClock.elapsedRealtime() - segmentStart
                waitingForInput = true
                listener.onInputRequested(msg.data.getString(RunnerProtocol.KEY_TEXT).orEmpty())
            }
            RunnerProtocol.MSG_FINISHED -> if (running && killReason == null) {
                finish(RunOutcome.Completed(msg.data.getString(RunnerProtocol.KEY_STATUS) ?: "ok"))
            }
            // Previous run still cleaning up in the service: retry shortly.
            RunnerProtocol.MSG_BUSY -> if (running && killReason == null) lastCode?.let { code -> main.postDelayed({ if (running) sendRun(code) }, 50) }
        }
    }

    private fun setEngine(state: EngineState) {
        engine = state
        listener.onEngineState(state)
    }

    private fun send(msg: Message) {
        val s = service ?: return
        try { s.send(msg) } catch (_: RemoteException) { processDied() }
    }

    companion object {
        const val DEFAULT_TIME_LIMIT_MS = 10_000L
        private const val WATCHDOG_TICK_MS = 100L
    }
}
