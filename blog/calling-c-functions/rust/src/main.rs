//! Call the C `mathlib` shared library from Rust.
//!
//! Rust speaks the C ABI natively: we declare the functions in an
//! `unsafe extern "C"` block (Rust 2024 edition) and the linker (wired up in
//! build.rs) connects them to the prebuilt shared library. `#[repr(C)]` makes
//! our struct layout match C exactly.

use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_int, c_long};

#[repr(C)]
#[derive(Clone, Copy, Debug)]
struct Point {
    x: f64,
    y: f64,
}

// Every call into C is `unsafe`: the compiler can't verify the other side.
unsafe extern "C" {
    fn add(a: c_int, b: c_int) -> c_int;
    fn fibonacci(n: c_int) -> c_long;
    fn greet(name: *const c_char) -> *mut c_char;
    fn free_string(s: *mut c_char);
    fn distance(a: Point, b: Point) -> f64;
    fn midpoint(a: Point, b: Point) -> Point;
    fn sum_array(values: *const c_int, count: usize) -> c_long;
}

/// Safe wrapper around `greet` that handles the C-owned string's lifetime.
fn greet_safe(name: &str) -> String {
    let c_name = CString::new(name).expect("name contained a NUL byte");
    unsafe {
        let ptr = greet(c_name.as_ptr());
        // Copy the bytes into an owned Rust String while the C memory is alive.
        let owned = CStr::from_ptr(ptr).to_string_lossy().into_owned();
        free_string(ptr); // hand ownership back to C
        owned
    }
}

fn main() {
    println!("== scalars ==");
    unsafe {
        println!("add(2, 3)        = {}", add(2, 3));
        println!("fibonacci(20)    = {}", fibonacci(20));
    }

    println!("\n== strings ==");
    println!("greet(\"Ada\")     = {}", greet_safe("Ada"));

    println!("\n== structs & arrays ==");
    let a = Point { x: 0.0, y: 0.0 };
    let b = Point { x: 3.0, y: 4.0 };
    let numbers: [c_int; 10] = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
    unsafe {
        println!("distance(a, b)   = {}", distance(a, b));
        let m = midpoint(a, b);
        println!("midpoint(a, b)   = Point {{ x: {}, y: {} }}", m.x, m.y);
        println!(
            "sum_array(1..10) = {}",
            sum_array(numbers.as_ptr(), numbers.len())
        );
    }
}
