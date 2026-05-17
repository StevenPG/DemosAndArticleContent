# JNI Example

Demonstrates Java Native Interface (JNI) - Java's original mechanism for calling native C/C++ code, available since Java 1.1.

## What This Project Shows

| Class | What It Demonstrates |
|---|---|
| `NativeStringUtils` | Passing strings between Java and C, returning new strings |
| `NativeArrayOps` | Array pinning, in-place modification, creating new arrays from native code |
| `NativeSystemInfo` | Calling POSIX system functions (gethostname, getpid, malloc/free) |
| `NativeCallback` | **Upcalls** - C code calling back into Java (progress reporting, string transform) |
| `NativeThreading` | **Multi-threading** - Native threads with AttachCurrentThread, global refs, JNI_OnLoad |

## How JNI Works

The JNI workflow has multiple steps:

1. **Declare** `native` methods in Java
2. **Compile** the Java class
3. **Generate** C header files with `javac -h`
4. **Implement** the C functions following the exact naming convention
5. **Compile** the C code into a shared library (`.so`/`.dylib`/`.dll`)
6. **Load** the library at runtime with `System.loadLibrary()`

```
┌──────────────┐     javac -h     ┌──────────────┐
│  Java class   │ ──────────────► │  C header     │
│  (native      │                 │  (.h file)    │
│   methods)    │                 └──────┬───────┘
└──────┬───────┘                        │
       │                                ▼
       │                         ┌──────────────┐
       │                         │  C impl      │  ◄── You write this
       │                         │  (.c file)   │
       │                         └──────┬───────┘
       │                                │ gcc -shared
       │                                ▼
       │                         ┌──────────────┐
       │  System.loadLibrary()   │  Shared lib  │
       │ ◄────────────────────── │  (.so/.dylib)│
       ▼                         └──────────────┘
┌──────────────┐
│  JVM calls   │
│  native code │
└──────────────┘
```

## Running

```bash
./gradlew :jni-example:run
```

This will:
1. Compile the Java sources
2. Generate JNI headers from the `native` method declarations
3. Compile the C code into `libjniexamples.so`
4. Run the Java application with the native library on the library path

### Prerequisites

- Java 25
- GCC (or compatible C compiler)
- Linux (see parent README for macOS/Windows notes)

<!-- TODO: Paste actual output here after running -->

### Expected Output

```
=== JNI (Java Native Interface) Examples ===
Java Version: 25

--- String Operations (NativeStringUtils) ---
Original:    Hello from JNI!
Uppercase:   HELLO FROM JNI!
Reversed:    !INJ morf olleH
Count 'l':   2

--- Array Operations (NativeArrayOps) ---
Original:    [42, 17, 8, 99, 3, 61, 25]
Sum:         255
Scaled (x3): [126, 51, 24, 297, 9, 183, 75]
Sorted:      [3, 8, 17, 25, 42, 61, 99]

--- System Info (NativeSystemInfo) ---
Hostname:    <your-hostname>
Process ID:  <your-pid>
Native mem:  JNI native memory test

--- Callbacks / Upcalls (NativeCallback) ---
C code calling back into Java to report progress:
  [Callback from C] Step 1: 20.0% complete
  [Callback from C] Step 2: 40.0% complete
  [Callback from C] Step 3: 60.0% complete
  [Callback from C] Step 4: 80.0% complete
  [Callback from C] Step 5: 100.0% complete

C code calling Java's transformString() for each element:
Input:  [hello, world, jni]
Output: [[HELLO], [WORLD], [JNI]]

--- Multi-Threading (NativeThreading) ---
Native threads calling back into Java via AttachCurrentThread:
  [Thread 0 → Java] result=<computed> (Java thread: Thread-N)
  [Thread 1 → Java] result=<computed> (Java thread: Thread-N)
  [Thread 2 → Java] result=<computed> (Java thread: Thread-N)
  [Thread 3 → Java] result=<computed> (Java thread: Thread-N)
```

## Key JNI Concepts to Note

### The GetXxx/ReleaseXxx Pattern
Every JNI resource access follows a get/release pattern. Forgetting to release causes memory leaks:
```c
const char *str = (*env)->GetStringUTFChars(env, input, NULL);
// ... use str ...
(*env)->ReleaseStringUTFChars(env, input, str);  // MUST call this
```

### Array Pinning
When you call `GetIntArrayElements`, the JVM may pin the array in memory (preventing GC from moving it) or copy it. The release mode flag controls what happens on release:
- `0`: Copy changes back and free the buffer
- `JNI_COMMIT`: Copy changes back but don't free (for incremental updates)
- `JNI_ABORT`: Free the buffer without copying changes back (read-only use)

### Error Handling
Native code can throw Java exceptions using `ThrowNew`:
```c
(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/IOException"), "error message");
```
But the exception doesn't immediately unwind - you must return from the native function for it to propagate.

### Naming Convention
JNI function names MUST follow: `Java_<package>_<ClassName>_<methodName>`. This is rigid and gets unwieldy with deeply nested packages. Renaming a Java package means renaming all your C functions too.

### Callbacks (Upcalls) - C Calling Java
To call a Java method from C, you need three lookups:
```c
jclass cls = (*env)->GetObjectClass(env, obj);
jmethodID method = (*env)->GetMethodID(env, cls, "onProgress", "(ID)V");
(*env)->CallVoidMethod(env, obj, method, step, percent);
```
The `"(ID)V"` is a JNI type signature: `I`=int, `D`=double, `V`=void return. Use `javap -s ClassName` to find these signatures.

### Threading - JNIEnv* Is Thread-Local
The most dangerous JNI rule: **JNIEnv* must not be shared across threads**. If native code creates threads (e.g., via `pthread_create`), each thread must:
1. Call `AttachCurrentThread()` to get its own `JNIEnv*`
2. Use that `JNIEnv*` for all JNI calls on that thread
3. Call `DetachCurrentThread()` before the thread exits

Cache the `JavaVM*` pointer in `JNI_OnLoad()` and use it for `AttachCurrentThread()` from any thread.

### Local vs Global References
- **Local references** (default): Valid only during the current JNI call. Automatically cleaned up.
- **Global references** (`NewGlobalRef`): Valid from any thread, survive across JNI calls. Must be explicitly deleted with `DeleteGlobalRef`.
- In loops, call `DeleteLocalRef` to avoid exhausting the local reference table (typically 512 slots).
