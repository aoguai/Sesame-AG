package io.github.aoguai.sesameag.hook.rpc.bridge

/**
 * RPC版本枚举
 *
 * @property code 版本代码
 */
enum class RpcVersion(val code: String) {
    OLD("OLD"),
    NEW("NEW");

    companion object {
        private val MAP: Map<String, RpcVersion> = values().associateBy { it.code }

        /**
         * 根据代码获取RPC版本
         *
         * @param code 版本代码
         * @return 对应的RPC版本，如果不存在则返回null
         */
        @JvmStatic
        fun getByCode(code: String): RpcVersion? = MAP[code]
    }
}

