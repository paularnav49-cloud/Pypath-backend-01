"""
PyPath Practical: kernel-level network lock for the ``:python`` runner process.

The app needs the Android INTERNET permission for cloud sync (Supabase), and Android
grants that permission to every process of the app, including ``:python`` where learner
code runs. This module takes network access away from that process again, using a Linux
seccomp-bpf filter installed by trusted runner code before any learner code runs:

* ``socket()`` is refused with EPERM for every domain except AF_NETLINK and AF_UNIX
  datagram sockets (system logging). That blocks IPv4/IPv6 TCP/UDP, raw/packet sockets and
  stream/seqpacket Unix sockets, including Android's DNS proxy (``dnsproxyd``).
* ``ptrace``, ``process_vm_readv/writev`` and ``io_uring_*`` are refused (io_uring can open
  sockets without the socket() syscall).
* On 32-bit kernels' ``socketcall(SYS_SOCKET)`` is refused; a foreign system-call ABI
  (for example x32 or i386 calls from a 64-bit process) is refused completely.

``PR_SET_NO_NEW_PRIVS`` + ``SECCOMP_FILTER_FLAG_TSYNC`` apply the filter to every thread of
the process, and every thread or child created later inherits it. A seccomp filter can
never be removed or loosened, not even by native code. The UI process (which does the
cloud sync) is a different process and is not affected.

The lock fails closed: ``lock_network()`` raises if the filter cannot be installed or the
self-test can still open an internet socket, and the runner then refuses to run code.
"""

import os
import platform

# Linux UAPI constants.
_PR_SET_NO_NEW_PRIVS = 38
_SECCOMP_SET_MODE_FILTER = 1
_SECCOMP_FILTER_FLAG_TSYNC = 1
RET_ALLOW = 0x7FFF0000
RET_EPERM = 0x00050000 | 1  # SECCOMP_RET_ERRNO | EPERM
# Classic BPF opcodes.
LD_ABS_W, JEQ, JGE, RET, AND = 0x20, 0x15, 0x35, 0x06, 0x54
_AF_UNIX, _AF_NETLINK, _SOCK_DGRAM = 1, 16, 2
# struct seccomp_data offsets (args are 64-bit; the low word comes first on little endian).
_OFF_NR, _OFF_ARCH, _OFF_ARG0, _OFF_ARG1 = 0, 4, 16, 24

# arch -> (AUDIT_ARCH_*, socket, seccomp, socketcall or None, always refused syscalls)
# Refused: ptrace, process_vm_readv, process_vm_writev, io_uring_setup/enter/register.
ARCHES = {
    "arm64": (0xC00000B7, 198, 277, None, (117, 270, 271, 425, 426, 427)),
    "arm": (0x40000028, 281, 383, 102, (26, 376, 377, 425, 426, 427)),
    "x86_64": (0xC000003E, 41, 317, None, (101, 310, 311, 425, 426, 427)),
    "x86": (0x40000003, 359, 354, 102, (26, 347, 348, 425, 426, 427)),
}

_locked_arch = None


def detect_arch(machine=None, pointer_size=None):
    if pointer_size is None:
        import ctypes
        pointer_size = ctypes.sizeof(ctypes.c_void_p)
    m = (machine if machine is not None else platform.machine()).lower()
    bits64 = pointer_size == 8
    if m.startswith(("aarch64", "arm")):
        return "arm64" if bits64 else "arm"
    if m in ("x86_64", "amd64", "i386", "i486", "i586", "i686", "x86"):
        return "x86_64" if bits64 else "x86"
    return None


def build_program(arch):
    """Returns the filter as a list of ``(code, jt, jf, k)`` classic BPF instructions."""
    audit, sock, _, socketcall, refused = ARCHES[arch]
    code = []

    def ins(op, k=0, jt=None, jf=None):
        code.append((op, jt, jf, k))

    def label(name):
        code.append(name)

    ins(LD_ABS_W, _OFF_ARCH)
    ins(JEQ, audit, None, "deny")
    ins(LD_ABS_W, _OFF_NR)
    if arch == "x86_64":
        ins(JGE, 0x40000000, "deny", None)  # x32 ABI
    for n in refused:
        ins(JEQ, n, "deny", None)
    ins(JEQ, sock, "socket", None)
    if socketcall is not None:
        ins(JEQ, socketcall, "socketcall", None)
    ins(RET, RET_ALLOW)
    label("socket")
    ins(LD_ABS_W, _OFF_ARG0)
    ins(JEQ, _AF_NETLINK, "socket_ok", None)
    ins(JEQ, _AF_UNIX, None, "deny")
    ins(LD_ABS_W, _OFF_ARG1)
    ins(AND, 0xF)  # strip SOCK_CLOEXEC / SOCK_NONBLOCK
    ins(JEQ, _SOCK_DGRAM, None, "deny")
    label("socket_ok")
    ins(RET, RET_ALLOW)
    if socketcall is not None:
        label("socketcall")
        ins(LD_ABS_W, _OFF_ARG0)
        ins(JEQ, 1, "deny", None)  # SYS_SOCKET: its arguments sit behind a pointer, so refuse it
        ins(RET, RET_ALLOW)
    label("deny")
    ins(RET, RET_EPERM)

    positions, index = {}, 0
    for c in code:
        if isinstance(c, str):
            positions[c] = index
        else:
            index += 1
    out, index = [], 0
    for c in code:
        if isinstance(c, str):
            continue
        op, jt, jf, k = c

        def rel(target):
            if target is None:
                return 0
            d = positions[target] - index - 1
            if not 0 <= d < 256:  # classic BPF only jumps forward, at most 255 instructions
                raise ValueError("bad jump in network filter")
            return d

        out.append((op, rel(jt), rel(jf), k))
        index += 1
    return out


def simulate(program, nr, arch_value, args=(0, 0, 0, 0, 0, 0)):
    """Runs the filter on one system call (used by tests; mirrors the kernel's BPF semantics)."""
    data = {_OFF_NR: nr, _OFF_ARCH: arch_value}
    for i, a in enumerate(args):
        data[16 + 8 * i] = a & 0xFFFFFFFF
    acc, pc = 0, 0
    while True:
        op, jt, jf, k = program[pc]
        if op == LD_ABS_W:
            acc = data.get(k, 0)
        elif op == AND:
            acc &= k
        elif op == JEQ:
            pc += jt if acc == k else jf
        elif op == JGE:
            pc += jt if acc >= k else jf
        elif op == RET:
            return k
        else:
            raise ValueError("unknown opcode")
        pc += 1


def is_locked():
    return _locked_arch is not None


def lock_network():
    """Installs the filter (once). Raises OSError if the process cannot be locked."""
    global _locked_arch
    if _locked_arch is not None:
        return _locked_arch
    import ctypes

    arch = detect_arch()
    if arch is None:
        raise OSError("network lock: unsupported CPU architecture " + platform.machine())
    try:
        libc = ctypes.CDLL("libc.so", use_errno=True)
    except OSError:
        libc = ctypes.CDLL(None, use_errno=True)

    class SockFilter(ctypes.Structure):
        _fields_ = [("code", ctypes.c_ushort), ("jt", ctypes.c_ubyte), ("jf", ctypes.c_ubyte), ("k", ctypes.c_uint)]

    class SockFprog(ctypes.Structure):
        _fields_ = [("len", ctypes.c_ushort), ("filter", ctypes.POINTER(SockFilter))]

    instructions = build_program(arch)
    filters = (SockFilter * len(instructions))(*[SockFilter(*i) for i in instructions])
    prog = SockFprog(len(instructions), filters)

    prctl = libc.prctl
    prctl.restype = ctypes.c_int
    prctl.argtypes = [ctypes.c_int, ctypes.c_ulong, ctypes.c_ulong, ctypes.c_ulong, ctypes.c_ulong]
    if prctl(_PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0:
        raise OSError(ctypes.get_errno(), "network lock: PR_SET_NO_NEW_PRIVS failed")
    syscall = libc.syscall
    syscall.restype = ctypes.c_long
    syscall.argtypes = [ctypes.c_long, ctypes.c_long, ctypes.c_long, ctypes.c_void_p]
    if syscall(ARCHES[arch][2], _SECCOMP_SET_MODE_FILTER, _SECCOMP_FILTER_FLAG_TSYNC, ctypes.addressof(prog)) != 0:
        raise OSError(ctypes.get_errno(), "network lock: seccomp filter could not be installed")

    # Self-test: internet and DNS-proxy sockets must now be refused.
    sock = libc.socket
    sock.restype = ctypes.c_int
    sock.argtypes = [ctypes.c_int, ctypes.c_int, ctypes.c_int]
    for domain, kind in ((2, 1), (2, 2), (10, 1), (10, 2), (1, 1)):
        fd = sock(domain, kind, 0)
        if fd >= 0:
            os.close(fd)
            raise OSError("network lock: self-test failed for socket(%d, %d)" % (domain, kind))
    _locked_arch = arch
    return arch
