package com.pypath.app.practical

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Hosts the real CPython interpreter (Chaquopy) in its own OS process (`android:process=":python"`).
 *
 * Why a separate process:
 *  - A runaway program (`while True: pass`) can be stopped instantly and reliably by killing
 *    this process. The UI process is never blocked and can never be frozen by learner code.
 *  - A crash or out-of-memory in learner code only takes down this process, not the app.
 *  - Learner code shares no memory with the UI process (no access to its objects or state).
 *
 * Console I/O travels over two pipes (see `pypath_runner.py`), so learner code never needs the
 * Java bridge, which is hidden from it while it runs.
 */
class PythonRunnerService : Service() {

    private val ipcThread = HandlerThread("python-ipc").apply { start() }
    private val ipc = Handler(ipcThread.looper) { msg -> handle(msg); true }
    private val messenger = Messenger(ipc)

    @Volatile private var client: Messenger? = null
    @Volatile private var runner: PyObject? = null
    @Volatile private var version: String = ""
    @Volatile private var initError: String? = null
    @Volatile private var running = false
    @Volatile private var inputStream: FileOutputStream? = null

    override fun onCreate() {
        super.onCreate()
        Thread({
            try {
                if (!Python.isStarted()) Python.start(AndroidPlatform(this))
                val module = Python.getInstance().getModule("pypath_runner")
                version = module.callAttr("warm_up", privateDir()).toString()
                runner = module
            } catch (t: Throwable) {
                Log.e(TAG, "Python failed to start", t)
                initError = t.message ?: t.toString()
            }
            ipc.post { announce() }
        }, "python-init").start()
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        ipcThread.quitSafely()
        super.onDestroy()
    }

    private fun privateDir(): String = (applicationInfo.dataDir ?: filesDir.parent).toString()

    private fun announce() {
        val c = client ?: return
        val err = initError
        val msg = if (err != null) {
            Message.obtain(null, RunnerProtocol.MSG_INIT_FAILED).apply { data = Bundle().apply { putString(RunnerProtocol.KEY_TEXT, err) } }
        } else if (runner != null) {
            Message.obtain(null, RunnerProtocol.MSG_READY, Process.myPid(), 0).apply {
                data = Bundle().apply { putString(RunnerProtocol.KEY_TEXT, version) }
            }
        } else return
        send(c, msg)
    }

    private fun handle(msg: Message) {
        when (msg.what) {
            RunnerProtocol.MSG_REGISTER -> { client = msg.replyTo; announce() }
            RunnerProtocol.MSG_RUN -> {
                val code = msg.data.getString(RunnerProtocol.KEY_CODE).orEmpty()
                val module = runner
                when {
                    module == null -> announce()
                    running -> client?.let { send(it, Message.obtain(null, RunnerProtocol.MSG_BUSY)) }
                    else -> execute(module, code)
                }
            }
            RunnerProtocol.MSG_INPUT -> {
                val text = msg.data.getString(RunnerProtocol.KEY_TEXT).orEmpty().replace("\r", "").replace("\n", " ")
                try {
                    inputStream?.apply { write((text + "\n").toByteArray(Charsets.UTF_8)); flush() }
                } catch (e: IOException) {
                    Log.w(TAG, "input pipe closed", e)
                }
            }
        }
    }

    private fun execute(module: PyObject, code: String) {
        running = true
        val out = ParcelFileDescriptor.createPipe()   // [0] read (service), [1] write (python)
        val inp = ParcelFileDescriptor.createPipe()   // [0] read (python),  [1] write (service)
        val workDir = File(cacheDir, "practical-sandbox").apply { deleteRecursively(); mkdirs() }
        inputStream = FileOutputStream(inp[1].fileDescriptor)
        val started = SystemClock.elapsedRealtime()
        val forwarder = OutputForwarder()
        val status = AtomicReference("ok")

        val reader = Thread({
            readFrames(out[0], forwarder)
            forwarder.flush()
            val elapsed = SystemClock.elapsedRealtime() - started
            closeQuietly(out[0]); closeQuietly(inp[1]); closeQuietly(inp[0])
            inputStream = null
            workDir.deleteRecursively()
            running = false // before FINISHED, so an immediate next RUN is accepted
            client?.let {
                send(it, Message.obtain(null, RunnerProtocol.MSG_FINISHED).apply {
                    data = Bundle().apply {
                        putString(RunnerProtocol.KEY_STATUS, status.get())
                        putLong(RunnerProtocol.KEY_ELAPSED, elapsed)
                    }
                })
            }
        }, "python-output")

        // Large stack so deep (but legal) recursion reaches Python's RecursionError, not a native crash.
        val exec = Thread(null, {
            client?.let { send(it, Message.obtain(null, RunnerProtocol.MSG_STARTED)) }
            val result = try {
                module.callAttr("run", code, out[1].fd, inp[0].fd, workDir.absolutePath, privateDir()).toString()
            } catch (t: Throwable) {
                Log.e(TAG, "runner failed", t)
                "internal:" + (t.message ?: t.toString())
            }
            status.set(result)
            closeQuietly(out[1]) // EOF for the reader → it reports MSG_FINISHED after draining output
        }, "python-exec", 16L * 1024 * 1024)

        reader.start()
        exec.start()
    }

    /** Reads `kind + u32 length + utf-8` frames written by pypath_runner until EOF. */
    private fun readFrames(fd: ParcelFileDescriptor, forwarder: OutputForwarder) {
        try {
            DataInputStream(FileInputStream(fd.fileDescriptor).buffered()).use { s ->
                while (true) {
                    val kind = try { s.readByte().toInt().toChar() } catch (e: EOFException) { break }
                    val len = s.readInt()
                    val bytes = ByteArray(len)
                    s.readFully(bytes)
                    val text = String(bytes, Charsets.UTF_8)
                    when (kind) {
                        'O' -> forwarder.add(RunnerProtocol.KIND_STDOUT, text)
                        'E' -> forwarder.add(RunnerProtocol.KIND_STDERR, text)
                        'I' -> {
                            forwarder.flush()
                            client?.let {
                                send(it, Message.obtain(null, RunnerProtocol.MSG_INPUT_REQUEST).apply {
                                    data = Bundle().apply { putString(RunnerProtocol.KEY_TEXT, text) }
                                })
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "output pipe error", e)
        }
    }

    /** Batches console text so a print-heavy loop doesn't flood the binder. */
    private inner class OutputForwarder {
        private val kinds = ArrayList<Int>()
        private val texts = ArrayList<String>()
        private var size = 0
        private var scheduled = false
        private val flushTask = Runnable { flush() }

        @Synchronized fun add(kind: Int, text: String) {
            if (kinds.isNotEmpty() && kinds.last() == kind) {
                texts[texts.size - 1] = texts.last() + text
            } else { kinds += kind; texts += text }
            size += text.length
            if (size >= 32_000) flush()
            else if (!scheduled) { scheduled = true; ipc.postDelayed(flushTask, 40) }
        }

        @Synchronized fun flush() {
            ipc.removeCallbacks(flushTask)
            scheduled = false
            if (kinds.isEmpty()) return
            val c = client
            if (c != null) {
                send(c, Message.obtain(null, RunnerProtocol.MSG_OUTPUT).apply {
                    data = Bundle().apply {
                        putIntArray(RunnerProtocol.KEY_KINDS, kinds.toIntArray())
                        putStringArrayList(RunnerProtocol.KEY_TEXTS, ArrayList(texts))
                    }
                })
            }
            kinds.clear(); texts.clear(); size = 0
        }
    }

    private fun send(to: Messenger, msg: Message) {
        try { to.send(msg) } catch (e: RemoteException) { Log.w(TAG, "client gone", e) }
    }

    private fun closeQuietly(fd: ParcelFileDescriptor) {
        try { fd.close() } catch (_: IOException) {}
    }

    private companion object { const val TAG = "PyPathRunner" }
}
