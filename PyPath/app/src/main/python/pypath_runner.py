"""
PyPath Practical runner.

Executes learner-written Python code with the real CPython interpreter bundled by
Chaquopy. It runs inside the app's dedicated ``:python`` process (see
PythonRunnerService), never in the UI process.

I/O protocol
------------
User code never talks to Java. All console traffic goes over two OS pipes that the
service creates for every run:

* ``out_fd``  (Python -> service): a stream of frames ``kind(1 byte) + length(4 bytes,
  big endian) + utf-8 payload`` where kind is

    ``O`` stdout text, ``E`` stderr text, ``I`` input() request (payload = prompt).

* ``in_fd``   (service -> Python): newline-terminated lines answering ``I`` frames.

Sandbox (best effort, defence in depth)
---------------------------------------
* A permanent audit hook (PEP 578, cannot be removed from Python) blocks, while user
  code is running: network sockets, subprocess/exec/fork/kill, ctypes, imports of the
  Java bridge / Android internals, introspection of function globals and frames, and
  any file access outside the per-run scratch directory and the read-only Python
  standard library.
* The Java bridge modules are removed from ``sys.modules`` for the duration of the run.
* The Android app itself has no INTERNET permission, so the OS blocks networking too.
* Each run gets a fresh ``__main__`` namespace and builtins are restored afterwards.
"""

import builtins
import io
import linecache
import os
import struct
import sys
import time
import traceback

FILENAME = "main.py"
MAX_OUTPUT_CHARS = 200_000
_FLUSH_SIZE = 4096
_FLUSH_SECONDS = 0.03

_os_write = os.write
_os_read = os.read
_monotonic = time.monotonic

# Top-level modules user code may not import.
BLOCKED_MODULES = frozenset({
    "java", "android", "chaquopy", "_chaquopy", "com", "androidx",
    "socket", "_socket", "ssl", "_ssl", "select", "selectors", "asyncio",
    "http", "urllib", "ftplib", "smtplib", "poplib", "imaplib", "telnetlib", "socketserver",
    "xmlrpc", "webbrowser", "subprocess", "_posixsubprocess", "multiprocessing",
    "_multiprocessing", "concurrent", "ctypes", "_ctypes", "pty", "resource",
    "signal", "faulthandler",
})

_PATH_EVENTS = {
    "os.listdir", "os.scandir", "os.chdir", "os.chmod", "os.chown", "os.chflags",
    "os.remove", "os.rmdir", "os.mkdir", "os.rename", "os.truncate", "os.utime",
    "os.link", "os.symlink", "os.listxattr", "os.getxattr", "os.setxattr", "os.removexattr",
    "shutil.copyfile", "shutil.copymode", "shutil.copystat", "shutil.copytree",
    "shutil.move", "shutil.rmtree", "shutil.chown", "glob.glob", "glob.glob/2", "os.walk",
    "os.fwalk", "pathlib.Path.glob", "pathlib.Path.rglob",
}
_WRITE_EVENTS = {
    "os.chmod", "os.chown", "os.chflags", "os.remove", "os.rmdir", "os.mkdir", "os.rename",
    "os.truncate", "os.utime", "os.link", "os.symlink", "os.setxattr", "os.removexattr",
    "shutil.copyfile", "shutil.copymode", "shutil.copystat", "shutil.copytree",
    "shutil.move", "shutil.rmtree", "shutil.chown",
}
_BLOCKED_EVENTS = {
    "subprocess.Popen", "os.system", "os.exec", "os.posix_spawn", "os.spawn", "os.fork",
    "os.forkpty", "os.kill", "os.killpg", "os.startfile", "os.add_dll_directory",
    "pty.spawn", "sys.setrecursionlimit", "sys.addaudithook", "sys.settrace", "sys.setprofile",
    "sys.remote_exec", "cpython.run_command", "cpython.run_file",
    "code.__new__", "function.__new__", "sys._current_frames", "sys.set_asyncgen_hooks",
    "_thread.start_new_thread", "webbrowser.open", "mmap.__new__", "msvcrt.locking",
}
_BLOCKED_PREFIXES = ("socket.", "ctypes.", "urllib.", "http.client.", "ftplib.", "smtplib.",
                     "poplib.", "imaplib.", "nntplib.", "telnetlib.", "winreg.", "_winapi.")


class SandboxViolation(PermissionError):
    """Raised when learner code tries something the Practical sandbox does not allow."""


SandboxViolation.__module__ = "builtins"  # show as "SandboxViolation: ..." in tracebacks


class _Guard:
    active = False
    read_roots = ()
    write_root = None
    # Chaquopy unpacks stdlib native modules from the APK on first import; writes there are
    # allowed only while the import system is on the call stack.
    extract_root = None


_guard = _Guard()
_hook_installed = False


def _real(path):
    try:
        if isinstance(path, bytes):
            path = os.fsdecode(path)
        if isinstance(path, int):
            return None
        return os.path.realpath(os.fspath(path))
    except Exception:
        return ""


def _inside(path, root):
    return root is not None and (path == root or path.startswith(root.rstrip("/") + "/"))


def _check_path(path, write):
    p = _real(path)
    if p is None:  # numeric file descriptor
        return
    if _inside(p, _guard.write_root):
        return
    if _inside(p, _guard.extract_root) and (not write or _importing()):
        return
    if not write and any(_inside(p, r) for r in _guard.read_roots):
        return
    raise SandboxViolation(f"Access to '{path}' is not allowed in PyPath Practical")


def _importing():
    f = sys._getframe(2)
    while f is not None:
        name = f.f_code.co_filename
        if name.startswith("<frozen importlib") or "importer" in name:
            return True
        f = f.f_back
    return False


def _audit(event, args):
    if not _guard.active:
        return
    if event in _BLOCKED_EVENTS or event.startswith(_BLOCKED_PREFIXES):
        raise SandboxViolation(f"'{event}' is not allowed in PyPath Practical")
    if event == "import":
        top = str(args[0]).split(".")[0]
        if top in BLOCKED_MODULES:
            raise ImportError(f"Module '{args[0]}' is not available in PyPath Practical")
    elif event == "open":
        path, mode = args[0], args[1]
        if path is None:
            return
        flags = args[2] if len(args) > 2 else 0
        write = bool(mode and any(ch in str(mode) for ch in "wax+")) or bool(
            isinstance(flags, int) and flags & (os.O_WRONLY | os.O_RDWR | os.O_CREAT | os.O_TRUNC | os.O_APPEND))
        _check_path(path, write)
    elif event in _PATH_EVENTS:
        write = event in _WRITE_EVENTS
        for a in args:
            if isinstance(a, (str, bytes, os.PathLike)):
                _check_path(a, write)


def _install_hook(private_dir=None):
    """Install the audit hook once. ``private_dir`` is the app's private data directory:
    no read root may contain it, so learner code can never read app data (progress, prefs)."""
    global _hook_installed
    if _hook_installed:
        return
    private = _real(private_dir) if private_dir else None
    roots = set()
    for entry in list(sys.path) + [sys.prefix, sys.exec_prefix, os.path.dirname(os.__file__)]:
        if entry:
            r = _real(entry)
            if r and r not in ("/", "") and not (private and _inside(private, r)):
                roots.add(r)
    for entry in sys.path:
        r = _real(entry) if entry else ""
        if "/AssetFinder/" in r + "/":
            _guard.extract_root = r[:r.index("/AssetFinder") + len("/AssetFinder")]
            break
    _guard.read_roots = tuple(sorted(roots))
    sys.addaudithook(_audit)
    _hook_installed = True


# ───────────────────────────── console channel ─────────────────────────────

class _Channel:
    def __init__(self, out_fd, in_fd):
        self.out_fd = out_fd
        self.in_fd = in_fd
        self.buffer = []
        self.buffered = 0
        self.last_flush = _monotonic()
        self.total = 0
        self.truncated = False
        self.pending_in = b""

    def _send(self, kind, text):
        data = text.encode("utf-8", "replace")
        view = memoryview(kind + struct.pack(">I", len(data)) + data)
        while view:
            n = _os_write(self.out_fd, view)
            view = view[n:]

    def flush(self):
        if self.buffer:
            text = "".join(self.buffer)
            self.buffer = []
            self.buffered = 0
            self._send(b"O", text)
        self.last_flush = _monotonic()

    def _limit(self, text):
        if self.truncated:
            return ""
        room = MAX_OUTPUT_CHARS - self.total
        if len(text) > room:
            self.truncated = True
            text = text[:max(room, 0)]
            self.flush()
            self._send(b"E", text)
            self._send(b"E", f"\n[Output limit of {MAX_OUTPUT_CHARS:,} characters reached - further output hidden]\n")
            return ""
        self.total += len(text)
        return text

    def stdout(self, text):
        text = self._limit(text)
        if not text:
            return
        self.buffer.append(text)
        self.buffered += len(text)
        if self.buffered >= _FLUSH_SIZE or _monotonic() - self.last_flush >= _FLUSH_SECONDS:
            self.flush()

    def stderr(self, text):
        text = self._limit(text)
        if text:
            self.flush()
            self._send(b"E", text)

    def readline(self, prompt):
        self.flush()
        self._send(b"I", prompt)
        while b"\n" not in self.pending_in:
            chunk = _os_read(self.in_fd, 4096)
            if not chunk:
                if self.pending_in:
                    line, self.pending_in = self.pending_in, b""
                    return line.decode("utf-8", "replace")
                return None
            self.pending_in += chunk
        line, _, self.pending_in = self.pending_in.partition(b"\n")
        return line.decode("utf-8", "replace")


class _ConsoleOut(io.TextIOBase):
    def __init__(self, write):
        self._write = write

    def writable(self):
        return True

    def write(self, s):
        if not isinstance(s, str):
            raise TypeError(f"write() argument must be str, not {type(s).__name__}")
        self._write(s)
        return len(s)

    def flush(self):
        _current_channel_flush()

    def isatty(self):
        return False

    @property
    def encoding(self):
        return "utf-8"

    @property
    def errors(self):
        return "strict"


class _ConsoleIn(io.TextIOBase):
    def __init__(self, channel):
        self._ch = channel

    def readable(self):
        return True

    def readline(self, size=-1):
        line = self._ch.readline("")
        return "" if line is None else line + "\n"

    def read(self, size=-1):
        return self.readline()

    def isatty(self):
        return False

    @property
    def encoding(self):
        return "utf-8"


_active_channel = None


def _current_channel_flush():
    if _active_channel is not None:
        _active_channel.flush()


def _make_input(channel):
    def input(prompt=""):  # noqa: A001 - mirrors builtins.input
        prompt = str(prompt)
        if prompt:
            channel.stdout(prompt)
        line = channel.readline(prompt)
        if line is None:
            raise EOFError("EOF when reading a line")
        return line
    return input


def _format_exception(exc):
    tb = exc.__traceback__
    while tb is not None and tb.tb_frame.f_code.co_filename != FILENAME:
        tb = tb.tb_next
    te = traceback.TracebackException(type(exc), exc, tb, compact=True)
    _strip_runner_frames(te)
    return "".join(te.format())


def _strip_runner_frames(te, _seen=None):
    """Hide this module's own frames (e.g. the audit hook) from learner tracebacks."""
    seen = _seen if _seen is not None else set()
    if id(te) in seen:
        return
    seen.add(id(te))
    me = os.path.realpath(__file__)
    te.stack = traceback.StackSummary.from_list(
        [f for f in te.stack if os.path.realpath(f.filename) != me and f.filename != "import.pxi"])
    for nxt in (te.__cause__, te.__context__):
        if nxt is not None:
            _strip_runner_frames(nxt, seen)


def _no_threads(*args, **kwargs):
    raise SandboxViolation("Starting threads is not allowed in PyPath Practical")


def _block_threads():
    """Python 3.11 has no audit event for thread creation, so patch the entry points."""
    import _thread
    saved = [(_thread, "start_new_thread", _thread.start_new_thread)]
    if hasattr(_thread, "start_joinable_thread"):
        saved.append((_thread, "start_joinable_thread", _thread.start_joinable_thread))
    threading = sys.modules.get("threading")
    if threading is not None:
        for name in ("_start_new_thread", "_start_joinable_thread"):
            if hasattr(threading, name):
                saved.append((threading, name, getattr(threading, name)))
    for mod, name, _ in saved:
        setattr(mod, name, _no_threads)
    return saved


def _restore_threads(saved):
    for mod, name, value in saved:
        setattr(mod, name, value)
    threading = sys.modules.get("threading")
    import _thread
    if threading is not None and getattr(threading, "_start_new_thread", None) is _no_threads:
        threading._start_new_thread = _thread.start_new_thread


def _guarded_import(original):
    def __import__(name, globals=None, locals=None, fromlist=(), level=0):
        if level == 0 and str(name).split(".")[0] in BLOCKED_MODULES:
            raise ImportError(f"Module '{name}' is not available in PyPath Practical")
        return original(name, globals, locals, fromlist, level)
    return __import__


def _hide_modules():
    hidden = {}
    for name in list(sys.modules):
        if name.split(".")[0] in ("java", "android", "chaquopy", "_chaquopy", "com", "androidx"):
            hidden[name] = sys.modules.pop(name)
    return hidden


def run(code, out_fd, in_fd, workdir, private_dir=None):
    """Execute ``code``. Returns 'ok', 'error' or 'exit:<code>'."""
    global _active_channel
    _install_hook(private_dir)
    channel = _Channel(out_fd, in_fd)
    saved_streams = (sys.stdout, sys.stderr, sys.stdin)
    saved_builtins = dict(builtins.__dict__)
    saved_cwd = os.getcwd()
    saved_path = list(sys.path)
    saved_meta = list(sys.meta_path)
    saved_hooks = list(sys.path_hooks)
    saved_main = sys.modules.get("__main__")
    status = "ok"
    hidden = {}
    threads = []
    try:
        _guard.write_root = _real(workdir)
        os.chdir(workdir)
        linecache.cache[FILENAME] = (len(code), None, code.splitlines(True), FILENAME)
        _active_channel = channel
        sys.stdout = _ConsoleOut(channel.stdout)
        sys.stderr = _ConsoleOut(channel.stderr)
        sys.stdin = _ConsoleIn(channel)
        builtins.input = _make_input(channel)
        builtins.__import__ = _guarded_import(saved_builtins["__import__"])
        main = type(sys)("__main__")
        main.__file__ = FILENAME
        main.__builtins__ = builtins
        sys.modules["__main__"] = main
        try:
            compiled = compile(code, FILENAME, "exec", dont_inherit=True)
        except (SyntaxError, ValueError) as e:
            channel.stderr(_format_exception(e))
            return "error"
        hidden = _hide_modules()
        threads = _block_threads()
        _guard.active = True
        try:
            exec(compiled, main.__dict__)
        except SystemExit as e:
            _guard.active = False
            code_ = e.code
            if code_ is None or code_ == 0:
                status = "exit:0"
            elif isinstance(code_, int):
                status = f"exit:{code_}"
            else:
                channel.stderr(f"{code_}\n")
                status = "exit:1"
        except BaseException as e:  # noqa: BLE001 - report every learner error
            _guard.active = False
            try:
                text = _format_exception(e)
            except BaseException as fe:  # pragma: no cover
                text = f"{type(e).__name__}: {e}\n(traceback unavailable: {fe!r})\n"
            channel.stderr(text)
            status = "error"
        finally:
            _guard.active = False
    finally:
        _guard.active = False
        try:
            channel.flush()
        except OSError:
            pass
        _active_channel = None
        _restore_threads(threads)
        sys.modules.update(hidden)
        if saved_main is not None:
            sys.modules["__main__"] = saved_main
        sys.stdout, sys.stderr, sys.stdin = saved_streams
        builtins.__dict__.clear()
        builtins.__dict__.update(saved_builtins)
        sys.path[:] = saved_path
        sys.meta_path[:] = saved_meta
        sys.path_hooks[:] = saved_hooks
        try:
            os.chdir(saved_cwd)
        except OSError:
            pass
        linecache.cache.pop(FILENAME, None)
    return status


def warm_up(private_dir=None):
    """Install the sandbox hook and pre-import runner modules so the first run is fast."""
    _install_hook(private_dir)
    traceback.TracebackException  # noqa: B018
    return sys.version
