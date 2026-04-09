package io.github.aoguai.sesameag.model

import io.github.aoguai.sesameag.model.modelFieldExt.BooleanModelField
import io.github.aoguai.sesameag.task.ModelTask
import io.github.aoguai.sesameag.util.Log
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * 模型基类
 * 
 * 所有功能模块的基类，定义了模型的基本结构和生命周期
 */
abstract class Model {
    @JvmField
    val enableField: BooleanModelField
    fun getEnableField(): BooleanModelField = enableField

    init {
        val nameStr = getName()
        val defaultValue = "基础" == nameStr
        enableField = BooleanModelField("enable", getEnableFieldName(), defaultValue)
    }

    open fun getEnableFieldName(): String = "开启" + (getName() ?: "")

    val isEnable: Boolean
        get() = enableField.value == true
    
    /** 获取模型类型 */
    open fun getType(): ModelType = ModelType.NORMAL
    
    /** 获取模型名称 */
    abstract fun getName(): String?
    
    /** 获取模型所属分组 */
    abstract fun getGroup(): ModelGroup?
    
    /** 获取模型图标 */
    abstract fun getIcon(): String?
    
    /** 获取模型字段 */
    abstract fun getFields(): ModelFields?
    
    /** 准备阶段，在boot之前调用 */
    open fun prepare() {}
    
    /** 启动阶段，在prepare之后调用 */
    open fun boot(classLoader: ClassLoader?) {}
    
    /** 销毁阶段，在模型卸载时调用 */
    open fun destroy() {}
    
    companion object {
        private const val TAG = "Model"
        
        /** 模型配置映射（按代码） */
        private val modelConfigMap: MutableMap<String, ModelConfig> = LinkedHashMap()
        
        /** 只读的模型配置映射 */
        private val readOnlyModelConfigMap: Map<String, ModelConfig> = 
            Collections.unmodifiableMap(modelConfigMap)
        
        /** 模型配置映射（按分组） */
        private val groupModelConfigMap: MutableMap<ModelGroup, MutableMap<String, ModelConfig>> = 
            LinkedHashMap()
        
        /** 模型实例映射（按类） */
        private val modelMap: MutableMap<Class<out Model>, Model> = ConcurrentHashMap()
        
        /** 模型类列表（保持顺序） */
        private val modelClazzList: List<Class<out Model>> = ModelOrder.allConfig
        
        /** 模型数组（保持顺序） */
        @JvmField
        val modelArray: Array<Model?> = arrayOfNulls(modelClazzList.size)
        
        /**
         * 获取模型配置映射
         * @return 只读的模型配置映射
         */
        @JvmStatic
        fun getModelConfigMap(): Map<String, ModelConfig> = readOnlyModelConfigMap
        
        /**
         * 获取指定分组的模型配置
         * @param modelGroup 模型分组
         * @return 该分组的模型配置映射
         */
        @JvmStatic
        fun getGroupModelConfig(modelGroup: ModelGroup): Map<String, ModelConfig> {
            val map = groupModelConfigMap[modelGroup]
            return if (map != null) {
                Collections.unmodifiableMap(map)
            } else {
                emptyMap()
            }
        }
        
        /**
         * 获取指定类型的模型实例
         * @param modelClazz 模型类
         * @return 模型实例，如果不存在则返回null
         */
        @JvmStatic
        fun <T : Model> getModel(modelClazz: Class<T>): T? {
            val model = modelMap[modelClazz]
            return if (modelClazz.isInstance(model)) {
                modelClazz.cast(model)
            } else {
                Log.error(TAG, "Model ${modelClazz.simpleName} not found.")
                null
            }
        }
        
        /**
         * 初始化所有模型
         * 销毁现有模型，然后创建新的模型实例
         */
        @JvmStatic
        @Synchronized
        fun initAllModel() {
            destroyAllModel()
            
            modelClazzList.forEachIndexed { i, modelClazz ->
                try {
                    // 创建模型实例
                    val model = modelClazz.getDeclaredConstructor().newInstance()
                    val modelConfig = ModelConfig(model)
                    
                    // 存储到数组和映射
                    modelArray[i] = model
                    modelMap[modelClazz] = model
                    
                    // 存储到配置映射
                    val modelCode = modelConfig.code
                    modelConfigMap[modelCode] = modelConfig
                    
                    // 存储到分组映射
                    val group = modelConfig.group
                    if (group != null) {
                        val groupMap = groupModelConfigMap.getOrPut(group) { LinkedHashMap() }
                        groupMap[modelCode] = modelConfig
                    }
                    
                } catch (e: Exception) {
                    Log.printStackTrace(e)
                }
            }
        }
        
        /**
         * 启动所有模型
         * 先调用prepare，然后对启用的模型调用boot
         * 
         * @param classLoader 类加载器
         */
        @JvmStatic
        @Synchronized
        fun bootAllModel(classLoader: ClassLoader?) {
            for (model in modelArray) {
                if (model == null) continue
                
                // 准备阶段
                try {
                    model.prepare()
                } catch (e: Exception) {
                    Log.printStackTrace(e)
                }
                
                // 启动阶段（仅启用的模型）
                try {
                    if (model.enableField.value == true) {
                        model.boot(classLoader)
                    }
                } catch (e: Exception) {
                    Log.printStackTrace(e)
                }
            }
        }
        
        /**
         * 销毁所有模型
         * 清理所有模型实例和映射
         */
        @JvmStatic
        @Synchronized
        fun destroyAllModel() {
            for (i in modelArray.indices) {
                val model = modelArray[i]
                if (model != null) {
                    try {
                        // 如果是任务类型，先停止任务
                        if (ModelType.TASK == model.getType()) {
                            (model as ModelTask).stopTask()
                        }
                        model.destroy()
                    } catch (e: Exception) {
                        Log.printStackTrace(e)
                    }
                    modelArray[i] = null
                }
            }
            
            // 清空所有映射
            modelMap.clear()
            modelConfigMap.clear()
            groupModelConfigMap.clear()
        }
    }
}

