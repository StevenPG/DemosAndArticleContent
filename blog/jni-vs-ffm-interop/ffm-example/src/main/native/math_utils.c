/*
 * A simple C library used by the FFM example.
 *
 * NOTICE: Unlike the JNI example's C code, this file has ZERO Java/JNI
 * dependencies. It's plain C that could be called from Python, Rust, Go,
 * or any other language. This is a key advantage of FFM - your native code
 * stays portable and doesn't need JNI-specific boilerplate.
 *
 * Compile: gcc -shared -fPIC -o libffmdemo.so math_utils.c
 */

#include <math.h>

typedef struct {
    double x;
    double y;
} Point2D;

int add(int a, int b) {
    return a + b;
}

long factorial(int n) {
    if (n <= 1) return 1;
    long result = 1;
    for (int i = 2; i <= n; i++) {
        result *= i;
    }
    return result;
}

long fibonacci(int n) {
    if (n <= 0) return 0;
    if (n == 1) return 1;
    long a = 0, b = 1;
    for (int i = 2; i <= n; i++) {
        long temp = a + b;
        a = b;
        b = temp;
    }
    return b;
}

double distance(const Point2D *p1, const Point2D *p2) {
    double dx = p2->x - p1->x;
    double dy = p2->y - p1->y;
    return sqrt(dx * dx + dy * dy);
}
