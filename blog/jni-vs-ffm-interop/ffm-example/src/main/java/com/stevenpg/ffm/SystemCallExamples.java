package com.stevenpg.ffm;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Demonstrates calling POSIX system functions using FFM.
 * These are the same system calls as the JNI example, but without
 * writing any C code - everything is pure Java.
 *
 * This is arguably FFM's biggest advantage over JNI: you can call any
 * native function directly from Java without a C wrapper.
 */
public class SystemCallExamples {

    public static void run() {
        System.out.println("--- System Calls (POSIX functions via FFM) ---");

        Linker linker = Linker.nativeLinker();
        SymbolLookup stdlib = linker.defaultLookup();

        try {
            callGetHostname(linker, stdlib);
            callGetPid(linker, stdlib);
            demonstrateMallocFree(linker, stdlib);
        } catch (Throwable e) {
            System.err.println("Error in system call examples: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    /**
     * Call gethostname() - direct system call from Java, no C wrapper needed.
     * C signature: int gethostname(char *name, size_t len)
     */
    private static void callGetHostname(Linker linker, SymbolLookup stdlib) throws Throwable {
        MemorySegment gethostnameAddr = stdlib.find("gethostname")
                .orElseThrow(() -> new RuntimeException("gethostname not found"));

        FunctionDescriptor desc = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,    // return: int (0 on success)
                ValueLayout.ADDRESS,     // param 1: char *name
                ValueLayout.JAVA_LONG    // param 2: size_t len
        );

        MethodHandle gethostname = linker.downcallHandle(gethostnameAddr, desc);

        try (Arena arena = Arena.ofConfined()) {
            // Allocate a buffer for the hostname
            MemorySegment buffer = arena.allocate(256);
            int result = (int) gethostname.invoke(buffer, 256L);
            if (result == 0) {
                String hostname = buffer.getString(0);
                System.out.println("Hostname:    " + hostname);
            } else {
                System.out.println("gethostname failed with code: " + result);
            }
        }
    }

    /**
     * Call getpid() - trivial system call, great for showing minimal FFM usage.
     * C signature: pid_t getpid(void)
     */
    private static void callGetPid(Linker linker, SymbolLookup stdlib) throws Throwable {
        MemorySegment getpidAddr = stdlib.find("getpid")
                .orElseThrow(() -> new RuntimeException("getpid not found"));

        FunctionDescriptor desc = FunctionDescriptor.of(ValueLayout.JAVA_INT);
        MethodHandle getpid = linker.downcallHandle(getpidAddr, desc);

        int pid = (int) getpid.invoke();
        System.out.println("Process ID:  " + pid);
    }

    /**
     * Demonstrates calling malloc/free directly from Java via FFM.
     * This is equivalent to the JNI allocateAndReadNativeMemory example,
     * but done entirely in pure Java.
     *
     * C signatures:
     *   void *malloc(size_t size)
     *   void free(void *ptr)
     */
    private static void demonstrateMallocFree(Linker linker, SymbolLookup stdlib) throws Throwable {
        // Look up malloc and free
        MethodHandle malloc = linker.downcallHandle(
                stdlib.find("malloc").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
        );

        MethodHandle free = linker.downcallHandle(
                stdlib.find("free").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );

        String message = "FFM native memory test";
        byte[] bytes = message.getBytes();

        // Allocate native memory via malloc
        MemorySegment ptr = ((MemorySegment) malloc.invoke((long) bytes.length + 1))
                .reinterpret(bytes.length + 1);  // Set the segment bounds

        // Write string bytes into native memory
        ptr.copyFrom(MemorySegment.ofArray(bytes));
        ptr.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);  // null terminator

        // Read back
        String readBack = ptr.getString(0);
        System.out.println("Native mem:  " + readBack);

        // Free the native memory
        free.invoke(ptr);

        // NOTE: With Arena-managed memory (shown in MemoryExamples), you
        // don't need to call free manually. This example uses malloc/free
        // directly to show the FFM equivalent of the JNI example.
    }
}
