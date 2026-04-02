#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>

/*
 * JNI String Operations
 *
 * Key JNI concepts demonstrated here:
 * - GetStringUTFChars / ReleaseStringUTFChars: Access Java string data as C strings.
 *   MUST always release after use to avoid memory leaks.
 * - NewStringUTF: Create a new Java String from a C string.
 * - The function naming convention is mandatory:
 *   Java_<package_with_underscores>_<ClassName>_<methodName>
 */

JNIEXPORT jstring JNICALL Java_com_stevenpg_jni_NativeStringUtils_toUpperCase
  (JNIEnv *env, jobject obj, jstring input) {
    // Get the C string from the Java string
    const char *str = (*env)->GetStringUTFChars(env, input, NULL);
    if (str == NULL) {
        return NULL; // OutOfMemoryError already thrown by JVM
    }

    size_t len = strlen(str);
    char *result = (char *)malloc(len + 1);
    if (result == NULL) {
        (*env)->ReleaseStringUTFChars(env, input, str);
        // Throw a Java exception from native code
        (*env)->ThrowNew(env,
            (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
            "Failed to allocate memory in native code");
        return NULL;
    }

    for (size_t i = 0; i < len; i++) {
        result[i] = toupper((unsigned char)str[i]);
    }
    result[len] = '\0';

    // IMPORTANT: Always release the string when done
    (*env)->ReleaseStringUTFChars(env, input, str);

    jstring jResult = (*env)->NewStringUTF(env, result);
    free(result);
    return jResult;
}

JNIEXPORT jstring JNICALL Java_com_stevenpg_jni_NativeStringUtils_reverse
  (JNIEnv *env, jobject obj, jstring input) {
    const char *str = (*env)->GetStringUTFChars(env, input, NULL);
    if (str == NULL) return NULL;

    size_t len = strlen(str);
    char *result = (char *)malloc(len + 1);
    if (result == NULL) {
        (*env)->ReleaseStringUTFChars(env, input, str);
        return NULL;
    }

    for (size_t i = 0; i < len; i++) {
        result[i] = str[len - 1 - i];
    }
    result[len] = '\0';

    (*env)->ReleaseStringUTFChars(env, input, str);

    jstring jResult = (*env)->NewStringUTF(env, result);
    free(result);
    return jResult;
}

JNIEXPORT jint JNICALL Java_com_stevenpg_jni_NativeStringUtils_countChar
  (JNIEnv *env, jobject obj, jstring input, jchar target) {
    const char *str = (*env)->GetStringUTFChars(env, input, NULL);
    if (str == NULL) return 0;

    int count = 0;
    for (size_t i = 0; i < strlen(str); i++) {
        if (str[i] == (char)target) {
            count++;
        }
    }

    (*env)->ReleaseStringUTFChars(env, input, str);
    return count;
}
