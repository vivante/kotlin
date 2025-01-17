// IGNORE_BACKEND: WASM
// FILE: test.kt

fun foo(n: Any) {
    if (1 == when (n) {
            is Int -> n
            else -> 1.0f
        }) {
    }
    if (1 != when (n) {
            is Int -> n
            else -> 1.0f
        }) {
    }
}

fun box() {
    foo(2)
}

// EXPECTATIONS JVM_IR
// test.kt:18 box
// test.kt:5 foo
// test.kt:6 foo
// test.kt:5 foo
// test.kt:10 foo
// test.kt:11 foo
// test.kt:10 foo
// test.kt:15 foo
// test.kt:19 box

// EXPECTATIONS JS_IR
// test.kt:18 box
// test.kt:6 foo
// test.kt:6 foo
// test.kt:5 foo
// test.kt:11 foo
// test.kt:11 foo
// test.kt:10 foo
// test.kt:15 foo
// test.kt:19 box
