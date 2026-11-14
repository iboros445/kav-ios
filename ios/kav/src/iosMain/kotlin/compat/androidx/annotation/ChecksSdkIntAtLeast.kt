// Lint's annotation for an SDK check, used on upstream's getters. It means nothing at run time.
package androidx.annotation

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
annotation class ChecksSdkIntAtLeast(val api: Int = -1, val codename: String = "", val parameter: Int = -1, val lambda: Int = -1)
