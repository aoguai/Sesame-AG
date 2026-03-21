package io.github.aoguai.sesameag.util

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

/**
 * 类型工具类
 * 提供了一系列方法来处理Java反射中的类型相关的操作
 */
object TypeUtil {
    
    /**
     * 从给定的类型中提取Class对象
     * 如果类型是Class、ParameterizedType或有界的TypeVariable/WildcardType，则返回其对应的Class对象
     *
     * @param type 给定的类型
     * @return 提取的Class对象，如果无法提取则返回null
     */
    @JvmStatic
    fun getClass(type: Type?): Class<*>? {
        if (type != null) {
            when (type) {
                is Class<*> -> return type
                is ParameterizedType -> return type.rawType as Class<*>
                is TypeVariable<*> -> {
                    val upperBounds = type.bounds
                    if (upperBounds.size == 1) {
                        return getClass(upperBounds[0])
                    }
                }
                is WildcardType -> {
                    val upperBounds = type.upperBounds
                    if (upperBounds.size == 1) {
                        return getClass(upperBounds[0])
                    }
                }
            }
        }
        return null
    }
    
    /**
     * 获取Field的泛型类型
     *
     * @param field Field对象
     * @return Field的泛型类型
     */
    @JvmStatic
    fun getType(field: Field?): Type? {
        return field?.genericType
    }
    
    /**
     * 获取Field的Class类型
     *
     * @param field Field对象
     * @return Field的Class类型
     */
    @JvmStatic
    fun getClass(field: Field?): Class<*>? {
        return field?.type
    }
    
    /**
     * 获取Method的第一个参数的泛型类型
     *
     * @param method Method对象
     * @return 第一个参数的泛型类型
     */
    @JvmStatic
    fun getFirstParamType(method: Method?): Type? {
        return getParamType(method, 0)
    }
    
    /**
     * 获取Method的第一个参数的Class类型
     *
     * @param method Method对象
     * @return 第一个参数的Class类型
     */
    @JvmStatic
    fun getFirstParamClass(method: Method?): Class<*>? {
        return getParamClass(method, 0)
    }
    
    /**
     * 获取Method指定索引位置参数的泛型类型
     *
     * @param method Method对象
     * @param index  参数索引
     * @return 指定索引位置参数的泛型类型
     */
    @JvmStatic
    fun getParamType(method: Method?, index: Int): Type? {
        val types = getParamTypes(method)
        return if (types != null && types.size > index) types[index] else null
    }
    
    /**
     * 获取Method指定索引位置参数的Class类型
     *
     * @param method Method对象
     * @param index  参数索引
     * @return 指定索引位置参数的Class类型
     */
    @JvmStatic
    fun getParamClass(method: Method?, index: Int): Class<*>? {
        val classes = getParamClasses(method)
        return if (classes != null && classes.size > index) classes[index] else null
    }
    
    /**
     * 获取Method的所有参数的泛型类型
     *
     * @param method Method对象
     * @return Method的所有参数的泛型类型
     */
    @JvmStatic
    fun getParamTypes(method: Method?): Array<Type>? {
        return method?.genericParameterTypes
    }
    
    /**
     * 获取Method的所有参数的Class类型
     *
     * @param method Method对象
     * @return Method的所有参数的Class类型
     */
    @JvmStatic
    fun getParamClasses(method: Method?): Array<Class<*>>? {
        return method?.parameterTypes
    }
    
    /**
     * 获取Method的返回值的泛型类型
     *
     * @param method Method对象
     * @return Method的返回值的泛型类型
     */
    @JvmStatic
    fun getReturnType(method: Method?): Type? {
        return method?.genericReturnType
    }
    
    /**
     * 获取Method的返回值的Class类型
     *
     * @param method Method对象
     * @return Method的返回值的Class类型
     */
    @JvmStatic
    fun getReturnClass(method: Method?): Class<*>? {
        return method?.returnType
    }
    
    /**
     * 获取泛型类型的参数类型
     *
     * @param type 泛型类型
     * @return 参数类型
     */
    @JvmStatic
    fun getTypeArgument(type: Type?): Type? {
        return getTypeArgument(type, 0)
    }
    
    /**
     * 获取泛型类型的指定索引位置的参数类型
     *
     * @param type  泛型类型
     * @param index 参数索引
     * @return 指定索引位置的参数类型
     */
    @JvmStatic
    fun getTypeArgument(type: Type?, index: Int): Type? {
        val typeArguments = getTypeArguments(type)
        return if (typeArguments != null && typeArguments.size > index) typeArguments[index] else null
    }
    
    /**
     * 获取泛型类型的所有参数类型
     *
     * @param type 泛型类型
     * @return 泛型类型的所有参数类型
     */
    @JvmStatic
    fun getTypeArguments(type: Type?): Array<Type>? {
        if (type == null) {
            return null
        }
        val parameterizedType = toParameterizedType(type)
        return parameterizedType?.actualTypeArguments
    }
    
    /**
     * 将类型转换为ParameterizedType
     *
     * @param type 泛型类型
     * @return ParameterizedType对象
     */
    @JvmStatic
    fun toParameterizedType(type: Type?): ParameterizedType? {
        return toParameterizedType(type, 0)
    }
    
    /**
     * 将类型转换为ParameterizedType，并指定接口索引
     *
     * @param type           泛型类型
     * @param interfaceIndex 接口索引
     * @return ParameterizedType对象
     */
    @JvmStatic
    fun toParameterizedType(type: Type?, interfaceIndex: Int): ParameterizedType? {
        return when (type) {
            is ParameterizedType -> type
            is Class<*> -> {
                val generics = getGenerics(type)
                if (generics.size > interfaceIndex) generics[interfaceIndex] else null
            }
            else -> null
        }
    }
    
    /**
     * 获取类的泛型类型
     *
     * @param clazz Class对象
     * @return 泛型类型数组
     */
    @JvmStatic
    fun getGenerics(clazz: Class<*>): Array<ParameterizedType> {
        val result = mutableListOf<ParameterizedType>()
        
        val genericSuper = clazz.genericSuperclass
        if (genericSuper != null && genericSuper != Any::class.java) {
            toParameterizedType(genericSuper)?.let { result.add(it) }
        }
        
        val genericInterfaces = clazz.genericInterfaces
        for (genericInterface in genericInterfaces) {
            toParameterizedType(genericInterface)?.let { result.add(it) }
        }
        
        return result.toTypedArray()
    }
    
    /**
     * 检查类型是否未知
     *
     * @param type 给定的类型
     * @return 如果类型未知或为TypeVariable，则返回true
     */
    @JvmStatic
    fun isUnknown(type: Type?): Boolean {
        return type == null || type is TypeVariable<*>
    }
    
    /**
     * 检查给定的类型数组中是否包含TypeVariable
     *
     * @param types 类型数组
     * @return 如果包含TypeVariable，则返回true
     */
    @JvmStatic
    fun hasTypeVariable(vararg types: Type): Boolean {
        return types.any { it is TypeVariable<*> }
    }
}

