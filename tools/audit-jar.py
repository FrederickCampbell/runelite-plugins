"""Read-only constant-pool screen of shipped classes; not a substitute for Hub review."""
import struct
import sys
import zipfile
from pathlib import Path

FORBIDDEN = (
    "java/lang/reflect/", "java/awt/Robot", "java/awt/KeyboardFocusManager",
    "java/awt/DefaultKeyboardFocusManager", "java/awt/MouseInfo", "java/awt/event/AWTEventListener",
    "java/lang/ProcessBuilder", "java/lang/Process", "java/net/URLClassLoader",
    "sun/misc/Unsafe", "jdk/internal/misc/Unsafe", "com/sun/jna/", "org/lwjgl/",
    "java/io/ObjectInputStream", "java/io/ObjectOutputStream", "java/io/FileInputStream",
    "java/io/FileOutputStream", "java/io/RandomAccessFile", "java/io/FileReader",
    "java/io/FileWriter", "java/nio/file/", "java/net/http/", "java/net/HttpURLConnection",
)
BLOCKED_METHODS = {
    "invokeMenuAction", "menuAction", "mousePress", "mouseRelease", "mouseMove",
    "keyPress", "keyRelease", "dispatchEvent", "postEvent", "requestFocus",
    "requestFocusInWindow", "createMenuEntry", "setMenuEntries", "setCanSendPackets",
    "hopToWorld", "setAccessible",
}
def forbidden(owner, method=""):
    if owner.startswith("[L"):
        owner = owner[2:-1]
    return any(owner.startswith(x) for x in FORBIDDEN) or method in BLOCKED_METHODS or (
        owner == "java/lang/Class" and (method == "forName" or method.startswith("getDeclared"))
    ) or (owner == "java/lang/Runtime" and method in ("exec", "getRuntime", "load", "loadLibrary")) or (
        owner == "java/lang/System" and method in ("load", "loadLibrary")
    ) or (owner == "java/lang/Thread" and method in ("sleep", "interrupt")) or (
        owner.startswith("java/lang/invoke/MethodHandles") and (method.startswith("find") or method.startswith("unreflect"))
    )

def inspect(data):
    assert data[:4] == b"\xca\xfe\xba\xbe"
    assert struct.unpack_from(">H", data, 6)[0] == 55, "Not Java 11 bytecode"
    count = struct.unpack_from(">H", data, 8)[0]
    entries = [None] * count
    pos, i = 10, 1
    while i < count:
        tag = data[pos]; pos += 1
        if tag == 1:
            n = struct.unpack_from(">H", data, pos)[0]; pos += 2
            entries[i] = (tag, data[pos:pos+n].decode("utf-8", errors="replace")); pos += n
        elif tag in (7, 8, 16, 19, 20):
            entries[i] = (tag, struct.unpack_from(">H", data, pos)[0]); pos += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            entries[i] = (tag, *struct.unpack_from(">HH", data, pos)); pos += 4
        elif tag in (3, 4):
            pos += 4
        elif tag in (5, 6):
            pos += 8; i += 1
        elif tag == 15:
            pos += 3
        else:
            raise ValueError(f"Unknown class constant tag {tag}")
        i += 1
    def utf(index):
        return entries[index][1]
    for entry in entries:
        if entry is None:
            continue
        if entry[0] == 7:
            owner = utf(entry[1])
            if forbidden(owner):
                yield owner
        elif entry[0] in (9, 10, 11):
            owner = utf(entries[entry[1]][1])
            name = utf(entries[entry[2]][1])
            if forbidden(owner, name):
                yield f"{owner}.{name}"

if __name__ == "__main__":
    jar = Path(sys.argv[1])
    with zipfile.ZipFile(jar) as z:
        classes = [n for n in z.namelist() if n.endswith(".class")]
        findings = [(n, ref) for n in classes for ref in inspect(z.read(n))]
    print(f"Compiled API screen: {len(classes)} Java 11 classes, {len(findings)} forbidden references")
    for path, ref in findings:
        print(path, ref)
    if not classes or findings:
        sys.exit(1)
