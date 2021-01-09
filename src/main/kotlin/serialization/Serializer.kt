package ru.yole.jkid.serialization

import ru.yole.jkid.*
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * 序列化接口
 * 将对象转为json字符串
 *
 * buildString函数是StringBuilder+apply()函数
 *
 * 总结：序列化接口的内部实现比较简单，主要是反射+注解的方式生成json字符串。
 * 反射是获取字段，注解是标识特殊属性
 */
fun serialize(obj: Any): String = buildString { serializeObject(obj) }

/**
 * 书本介绍的第一版，可以忽略
 */
private fun StringBuilder.serializeObjectWithoutAnnotation(obj: Any) {
    val kClass = obj.javaClass.kotlin
    val properties = kClass.memberProperties

    properties.joinToStringBuilder(this, prefix = "{", postfix = "}") { prop ->
        serializeString(prop.name)
        append(": ")
        serializePropertyValue(prop.get(obj))
    }
}

private fun StringBuilder.serializeObject(obj: Any) {
    //memberProperties用来获取类的所有属性
    //Kotlin的反射Api与Java的反射Api是2套，另外，为了减少包大小，Kotlin的反射Api需要额外依赖。
    obj.javaClass.kotlin.memberProperties
            //findAnnotation函数将返回一个注解，其类型就是指定为类型实参的类型，如果这个注解存在。
            //满足条件的不会过滤。
            .filter { it.findAnnotation<JsonExclude>() == null }
            .joinToStringBuilder(this, prefix = "{", postfix = "}") {
                serializeProperty(it, obj)
            }
}

private fun StringBuilder.serializeProperty(
        prop: KProperty1<Any, *>, obj: Any
) {
    //如果jsonNameAnn不为空，说明额外定义了名称
    val jsonNameAnn = prop.findAnnotation<JsonName>()
    val propName = jsonNameAnn?.name ?: prop.name

    //将java属性序列化为json属性
    serializeString(propName)
    append(": ")

    //将java属性值序列化为json属性值
    //如果有自定义的序列化器，就使用
    val value = prop.get(obj)
    val jsonValue = prop.getSerializer()?.toJsonValue(value) ?: value
    serializePropertyValue(jsonValue)
}

fun KProperty<*>.getSerializer(): ValueSerializer<Any?>? {
    val customSerializerAnn = findAnnotation<CustomSerializer>() ?: return null
    val serializerClass = customSerializerAnn.serializerClass

    val valueSerializer = serializerClass.objectInstance
            ?: serializerClass.createInstance()
    @Suppress("UNCHECKED_CAST")
    return valueSerializer as ValueSerializer<Any?>
}

private fun StringBuilder.serializePropertyValue(value: Any?) {
    when (value) {
        null -> append("null")
        is String -> serializeString(value)
        is Number, is Boolean -> append(value.toString())
        is List<*> -> serializeList(value)
        else -> serializeObject(value)
    }
}

private fun StringBuilder.serializeList(data: List<Any?>) {
    data.joinToStringBuilder(this, prefix = "[", postfix = "]") {
        serializePropertyValue(it)
    }
}

private fun StringBuilder.serializeString(s: String) {
    append('\"')
    s.forEach { append(it.escape()) }
    append('\"')
}

//对一些字符进行转义
private fun Char.escape(): Any =
        when (this) {
            '\\' -> "\\\\"
            '\"' -> "\\\""
            '\b' -> "\\b"
            '\u000C' -> "\\f"
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> this
        }
