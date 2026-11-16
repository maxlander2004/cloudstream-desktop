package android.content

/** Android's `ComponentName`, used to name an activity or receiver. */
open class ComponentName : Comparable<ComponentName> {
    private val packageName: String
    private val className: String

    constructor(packageName: String, className: String) {
        this.packageName = packageName
        this.className = className
    }

    constructor(context: Context, cls: Class<*>) {
        this.packageName = context.getPackageName()
        this.className = cls.name
    }

    constructor(parcel: Any?) : this("", "")

    open fun getPackageName(): String = packageName

    open fun getClassName(): String = className

    open fun getShortClassName(): String =
        if (className.startsWith("$packageName.")) className.removePrefix("$packageName.") else className

    open fun toShortString(): String = "$packageName/${getShortClassName()}"

    override fun compareTo(other: ComponentName): Int =
        (packageName + className).compareTo(other.packageName + other.className)

    override fun equals(other: Any?): Boolean =
        other is ComponentName && other.packageName == packageName && other.className == className

    override fun hashCode(): Int = packageName.hashCode() * 31 + className.hashCode()

    override fun toString(): String = "ComponentInfo{$packageName/$className}"
}
