#ifndef MATHLIB_H
#define MATHLIB_H

/*
 * mathlib - a deliberately tiny C library used to demonstrate calling C
 * from Python, Java, Rust and Go.
 *
 * It exercises the three things that make FFI interesting:
 *   1. plain scalars      (int / long)
 *   2. strings            (char* in, heap-allocated char* out)
 *   3. structs & arrays   (passed and returned by value / by pointer)
 */

#include <stddef.h>

/* ---- 1. scalars ---------------------------------------------------------- */

/* Add two integers. The "hello world" of FFI. */
int add(int a, int b);

/* Iterative Fibonacci. Returns the nth Fibonacci number (n >= 0). */
long fibonacci(int n);

/* ---- 2. strings ---------------------------------------------------------- */

/*
 * Return a newly malloc'd greeting, e.g. greet("Ada") -> "Hello, Ada!".
 * The caller owns the returned pointer and MUST release it with free_string().
 * This is the classic ownership wrinkle every language has to handle.
 */
char *greet(const char *name);

/* Free a string previously returned by greet(). */
void free_string(char *s);

/* ---- 3. structs & arrays ------------------------------------------------- */

/* A simple 2D point, passed and returned by value. */
typedef struct {
    double x;
    double y;
} Point;

/* Euclidean distance between two points (struct args by value). */
double distance(Point a, Point b);

/* Midpoint of two points (struct returned by value). */
Point midpoint(Point a, Point b);

/* Sum an array of ints given a pointer + length. */
long sum_array(const int *values, size_t count);

#endif /* MATHLIB_H */
