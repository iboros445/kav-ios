// Spike shim (Phase 0): proves Kotlin/Native accepts declarations in `package java.io`.
// Phase 1 replaces the body with an NSFileManager-backed implementation, keeping this surface.
package java.io

class File(val path: String) {
    constructor(parent: File, child: String) : this(parent.path.trimEnd('/') + "/" + child)

    val name: String get() = path.substringAfterLast('/')
    val absolutePath: String get() = path
    override fun toString(): String = path
    override fun equals(other: Any?): Boolean = other is File && other.path == path
    override fun hashCode(): Int = path.hashCode()
}
