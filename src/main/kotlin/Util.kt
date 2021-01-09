package ru.yole.jkid

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass

//KAnnotatedElement是Kotlin反射Api相关类的父类，如KClass
//KAnnotatedElement接口内部有属性annotations，
//它是一个由应用到源码中元素上的所有注解(具有运行时保留期)的实例组成的集合。
//这段代码可能有bug，如果一个属性有多个注解（包含T），并且第一个不是T，可能就会错过这个属性
inline fun <reified T> KAnnotatedElement.findAnnotation(): T?
        = annotations.filterIsInstance<T>().firstOrNull()

internal fun <T : Any> KClass<T>.createInstance(): T {
    val noArgConstructor = constructors.find {
        it.parameters.isEmpty()
    }
    noArgConstructor ?: throw IllegalArgumentException(
            "Class must have a no-argument constructor")

    return noArgConstructor.call()
}

fun Type.asJavaClass(): Class<Any> = when (this) {
    is Class<*> -> this as Class<Any>
    is ParameterizedType -> rawType as? Class<Any>
            ?: throw UnsupportedOperationException("Unknown type $this")
    else -> throw UnsupportedOperationException("Unknown type $this")
}

fun <T> Iterable<T>.joinToStringBuilder(stringBuilder: StringBuilder, separator: CharSequence = ", ", prefix: CharSequence = "", postfix: CharSequence = "", limit: Int = -1, truncated: CharSequence = "...", callback: ((T) -> Unit)? = null): StringBuilder {
    return joinTo(stringBuilder, separator, prefix, postfix, limit, truncated) {
        if (callback == null) return@joinTo it.toString()
        callback(it)
        ""
    }
}

fun Type.isPrimitiveOrString(): Boolean {
    val cls = this as? Class<Any> ?: return false
    return cls.kotlin.javaPrimitiveType != null || cls == String::class.java
}