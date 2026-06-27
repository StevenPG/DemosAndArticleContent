#include "mathlib.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int add(int a, int b) {
    return a + b;
}

long fibonacci(int n) {
    if (n < 2) {
        return n;
    }
    long prev = 0;
    long curr = 1;
    for (int i = 2; i <= n; i++) {
        long next = prev + curr;
        prev = curr;
        curr = next;
    }
    return curr;
}

char *greet(const char *name) {
    if (name == NULL) {
        name = "world";
    }
    /* "Hello, " + name + "!" + NUL */
    size_t len = strlen("Hello, ") + strlen(name) + strlen("!") + 1;
    char *result = malloc(len);
    if (result == NULL) {
        return NULL;
    }
    snprintf(result, len, "Hello, %s!", name);
    return result;
}

void free_string(char *s) {
    free(s);
}

double distance(Point a, Point b) {
    double dx = a.x - b.x;
    double dy = a.y - b.y;
    return sqrt(dx * dx + dy * dy);
}

Point midpoint(Point a, Point b) {
    Point m;
    m.x = (a.x + b.x) / 2.0;
    m.y = (a.y + b.y) / 2.0;
    return m;
}

long sum_array(const int *values, size_t count) {
    long total = 0;
    for (size_t i = 0; i < count; i++) {
        total += values[i];
    }
    return total;
}
