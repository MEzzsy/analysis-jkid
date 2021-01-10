package ru.yole.jkid.deserialization

import ru.yole.jkid.*
import ru.yole.jkid.serialization.getSerializer
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

/**
 * 之所以用一个类包装cacheData，是因为：
 * cacheData的get返回的是ClassInfo<*>对象，需要强转才能正常使用，
 * 每次调用的时候都需要强转，而且有可能导致类型错误。
 * 用一个额外的get方法可以解决这个问题。
 */
class ClassInfoCache {
    private val cacheData = mutableMapOf<KClass<*>, ClassInfo<*>>()

    /**
     * getOrPut，如果get为null就将lambda的内容put
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(cls: KClass<T>): ClassInfo<T> =
            cacheData.getOrPut(cls) { ClassInfo(cls) } as ClassInfo<T>
}

class ClassInfo<T : Any>(cls: KClass<T>) {
    private val className = cls.qualifiedName

    /**
     * 获取主构造器
     */
    private val constructor = cls.primaryConstructor
            ?: throw JKidException("Class ${cls.qualifiedName} doesn't have a primary constructor")

    /**
     * json中每个键对应构造器的形参
     */
    private val jsonNameToParamMap = hashMapOf<String, KParameter>()

    /**
     * 形参对应的序列化器
     */
    private val paramToSerializerMap = hashMapOf<KParameter, ValueSerializer<out Any?>>()

    /**
     * 存储指定DeserializeInterface注解的属性
     */
    private val jsonNameToDeserializeClassMap = hashMapOf<String, Class<out Any>?>()

    init {
        constructor.parameters.forEach { cacheDataForParameter(cls, it) }
    }

    private fun cacheDataForParameter(cls: KClass<*>, param: KParameter) {
        val paramName = param.name
                ?: throw JKidException("Class $className has constructor parameter without name")

        val property = cls.declaredMemberProperties.find { it.name == paramName } ?: return
        val name = property.findAnnotation<JsonName>()?.name ?: paramName
        jsonNameToParamMap[name] = param

        val deserializeClass = property.findAnnotation<DeserializeInterface>()?.targetClass?.java
        jsonNameToDeserializeClassMap[name] = deserializeClass

        val valueSerializer = property.getSerializer()
                ?: serializerForType(param.type.javaType)
                ?: return
        paramToSerializerMap[param] = valueSerializer
    }

    fun getConstructorParameter(propertyName: String): KParameter = jsonNameToParamMap[propertyName]
            ?: throw JKidException("Constructor parameter $propertyName is not found for class $className")

    fun getDeserializeClass(propertyName: String) = jsonNameToDeserializeClassMap[propertyName]

    fun deserializeConstructorArgument(param: KParameter, value: Any?): Any? {
        val serializer = paramToSerializerMap[param]
        if (serializer != null) return serializer.fromJsonValue(value)

        validateArgumentType(param, value)
        return value
    }

    private fun validateArgumentType(param: KParameter, value: Any?) {
        if (value == null && !param.type.isMarkedNullable) {
            throw JKidException("Received null value for non-null parameter ${param.name}")
        }
        if (value != null && value.javaClass != param.type.javaType) {
            throw JKidException("Type mismatch for parameter ${param.name}: " +
                    "expected ${param.type.javaType}, found ${value.javaClass}")
        }
    }

    fun createInstance(arguments: Map<KParameter, Any?>): T {
        ensureAllParametersPresent(arguments)
        return constructor.callBy(arguments)
    }

    private fun ensureAllParametersPresent(arguments: Map<KParameter, Any?>) {
        for (param in constructor.parameters) {
            if (arguments[param] == null && !param.isOptional && !param.type.isMarkedNullable) {
                throw JKidException("Missing value for parameter ${param.name}")
            }
        }
    }
}

class JKidException(message: String) : Exception(message)
