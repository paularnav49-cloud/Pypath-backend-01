package com.pypath.app.practical

/**
 * Messages exchanged between the UI process ([PythonRunner]) and the isolated
 * `:python` process ([PythonRunnerService]) over a [android.os.Messenger].
 */
internal object RunnerProtocol {
    // UI → service
    const val MSG_REGISTER = 1        // replyTo = client messenger
    const val MSG_RUN = 2             // data[KEY_CODE]
    const val MSG_INPUT = 3           // data[KEY_TEXT]

    // service → UI
    const val MSG_READY = 10          // arg1 = pid of the python process, data[KEY_TEXT] = python version
    const val MSG_INIT_FAILED = 11    // data[KEY_TEXT] = error
    const val MSG_STARTED = 12        // execution of user code has begun
    const val MSG_OUTPUT = 13         // data[KEY_KINDS] = IntArray, data[KEY_TEXTS] = ArrayList<String>
    const val MSG_INPUT_REQUEST = 14  // data[KEY_TEXT] = prompt
    const val MSG_FINISHED = 15       // data[KEY_STATUS], data[KEY_ELAPSED]
    const val MSG_BUSY = 16           // a RUN arrived while code was already running

    const val KEY_CODE = "code"
    const val KEY_TEXT = "text"
    const val KEY_KINDS = "kinds"
    const val KEY_TEXTS = "texts"
    const val KEY_STATUS = "status"
    const val KEY_ELAPSED = "elapsed"

    const val KIND_STDOUT = 0
    const val KIND_STDERR = 1
}
