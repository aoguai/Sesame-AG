package io.github.aoguai.sesameag.task.AnswerAI

import io.github.aoguai.sesameag.model.Model
import io.github.aoguai.sesameag.model.ModelFields
import io.github.aoguai.sesameag.model.ModelGroup
import io.github.aoguai.sesameag.model.buildModelFields
import io.github.aoguai.sesameag.model.withDesc
import io.github.aoguai.sesameag.model.modelFieldExt.ChoiceModelField
import io.github.aoguai.sesameag.model.modelFieldExt.StringModelField
import io.github.aoguai.sesameag.model.modelFieldExt.TextModelField
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.LogCatalog
import io.github.aoguai.sesameag.util.LogChannel

class AnswerAI : Model() {

    override fun getName(): String {
        return "AI答题"
    }

    override fun getGroup(): ModelGroup {
        return ModelGroup.OTHER
    }

    override fun getIcon(): String {
        return "ic_answer_ai"
    }

    object AIType {
        const val TONGYI = 0
        const val GEMINI = 1
        const val DEEPSEEK = 2
        const val CUSTOM = 3

        val nickNames = arrayOf(
            "通义千问",
            "Gemini",
            "DeepSeek",
            "自定义"
        )
    }

    private lateinit var getTongyiAIToken: TextModelField.UrlTextModelField
    private lateinit var tongYiToken: StringModelField
    private lateinit var getGeminiAIToken: TextModelField.UrlTextModelField
    private lateinit var GeminiToken: StringModelField
    private lateinit var getDeepSeekToken: TextModelField.UrlTextModelField
    private lateinit var DeepSeekToken: StringModelField
    private lateinit var getCustomServiceToken: TextModelField.ReadOnlyTextModelField
    private lateinit var CustomServiceToken: StringModelField
    private lateinit var CustomServiceUrl: StringModelField
    private lateinit var CustomServiceModel: StringModelField

    override fun getFields(): ModelFields = buildModelFields {
        choice("useGeminiAI", "AI类型", AIType.TONGYI, AIType.nickNames, desc = "选择当前用于自动答题的 AI 服务；关闭模块总开关后会退回普通答题逻辑。") { aiType = it }
        urlText("getTongyiAIToken", "通义千问 | 获取令牌", "https://help.aliyun.com/zh/dashscope/developer-reference/acquisition-and-configuration-of-api-key", desc = "打开通义千问官方文档查看 API Key 的申请与配置方式，仅在 AI 类型选择通义千问时显示。", dependency = "useGeminiAI=${AIType.TONGYI}") { getTongyiAIToken = it }
        string("tongYiToken", "qwen-turbo | 设置令牌", "", desc = "填写通义千问的 DashScope API Key；未填写或失效时无法使用通义千问答题。", dependency = "useGeminiAI=${AIType.TONGYI}") { tongYiToken = it }
        urlText("getGeminiAIToken", "Gemini | 获取令牌", "https://aistudio.google.com/app/apikey", desc = "打开 Gemini 官方密钥页面获取 API Key，仅在 AI 类型选择 Gemini 时显示。", dependency = "useGeminiAI=${AIType.GEMINI}") { getGeminiAIToken = it }
        string("GeminiAIToken", "gemini-1.5-flash | 设置令牌", "", desc = "填写 Gemini API Key；用于调用 gemini-1.5-flash 模型进行答题。", dependency = "useGeminiAI=${AIType.GEMINI}") { GeminiToken = it }
        urlText("getDeepSeekToken", "DeepSeek | 获取令牌", "https://platform.deepseek.com/usage", desc = "打开 DeepSeek 开放平台查看 API Key 获取方式，仅在 AI 类型选择 DeepSeek 时显示。", dependency = "useGeminiAI=${AIType.DEEPSEEK}") { getDeepSeekToken = it }
        string("DeepSeekToken", "DeepSeek-R1 | 设置令牌", "", desc = "填写 DeepSeek API Key；用于调用 DeepSeek-R1 模型进行答题。", dependency = "useGeminiAI=${AIType.DEEPSEEK}") { DeepSeekToken = it }
        readOnlyText("getCustomServiceToken", "粉丝福利😍", "下面这个不用动可以白嫖到3月10号让我们感谢讯飞大善人🙏", desc = "仅作当前默认自定义服务的提示说明；如果你有自己的兼容服务，可直接改下面三项配置。", dependency = "useGeminiAI=${AIType.CUSTOM}") { getCustomServiceToken = it }
        string("CustomServiceToken", "自定义服务 | 设置令牌", "sk-pQF9jek0CTTh3boKDcA9DdD7340a4e929eD00a13F681Cd8e", desc = "填写兼容 OpenAI 接口的自定义服务令牌，仅在 AI 类型选择自定义服务时生效。", dependency = "useGeminiAI=${AIType.CUSTOM}") { CustomServiceToken = it }
        string("CustomServiceBaseUrl", "自定义服务 | 设置BaseUrl", "https://maas-api.cn-huabei-1.xf-yun.com/v1", desc = "填写自定义服务的接口根地址，通常形如 https://host/v1，仅在 AI 类型选择自定义服务时生效。", dependency = "useGeminiAI=${AIType.CUSTOM}") { CustomServiceUrl = it }
        string("CustomServiceModel", "自定义服务 | 设置模型", "xdeepseekr1", desc = "填写自定义服务实际使用的模型名称，仅在 AI 类型选择自定义服务时生效。", dependency = "useGeminiAI=${AIType.CUSTOM}") { CustomServiceModel = it }
    }

    override fun boot(classLoader: ClassLoader?) {
        try {
            enable = enableField.value == true
            val selectedType = getSafeAiType()
            Log.runtime(String.format("初始化AI服务：已选择[%s]", AIType.nickNames[selectedType]))
            initializeAIService(selectedType)
        } catch (e: Exception) {
            Log.error(TAG, "初始化AI服务失败: ${e.message}")
            Log.printStackTrace(TAG, e)
            disableAIService()
        }
    }

    override fun destroy() {
        disableAIService()
    }

    private fun initializeAIService(selectedType: Int) {
        val safeType = selectedType.coerceIn(0, AIType.nickNames.lastIndex)
        val nextService = when (safeType) {
            AIType.TONGYI -> TongyiAI(tongYiToken.value)
            AIType.GEMINI -> GeminiAI(GeminiToken.value)
            AIType.DEEPSEEK -> DeepSeek(DeepSeekToken.value)
            AIType.CUSTOM -> {
                val service = CustomService(CustomServiceToken.value, CustomServiceUrl.value)
                service.setModelName(CustomServiceModel.value ?: "")
                Log.runtime(
                    String.format(
                        "已配置自定义服务：URL=[%s], Model=[%s]",
                        CustomServiceUrl.value,
                        CustomServiceModel.value
                    )
                )
                service
            }
            else -> AnswerAIInterface.getInstance()
        }
        answerAIInterface?.release()
        answerAIInterface = nextService
    }

    private fun disableAIService() {
        enable = false
        answerAIInterface?.release()
        answerAIInterface = null
    }

    companion object {
        private val TAG = AnswerAI::class.java.simpleName
        private const val QUESTION_LOG_FORMAT = "题目📒 [%s] | 选项: %s"
        private const val AI_ANSWER_LOG_FORMAT = "AI回答🧠 [%s] | AI类型: [%s] | 模型名称: [%s]"
        private const val NORMAL_ANSWER_LOG_FORMAT = "普通回答🤖 [%s]"
        private const val ERROR_AI_ANSWER = "AI回答异常：无法获取有效答案，请检查AI服务配置是否正确"

        private var enable = false
        private var answerAIInterface: AnswerAIInterface? = null
        private var aiType: ChoiceModelField? = null

        private fun getSafeAiType(): Int {
            return (aiType?.value ?: AIType.TONGYI).coerceIn(0, AIType.nickNames.lastIndex)
        }

        private fun resolveLogChannel(flag: String): LogChannel {
            val channel = LogCatalog.findByLoggerName(flag.trim()) ?: return LogChannel.COMMON
            return when (channel) {
                LogChannel.COMMON,
                LogChannel.FOREST,
                LogChannel.ORCHARD,
                LogChannel.FARM,
                LogChannel.STALL,
                LogChannel.OCEAN,
                LogChannel.MEMBER,
                LogChannel.SPORTS,
                LogChannel.GREEN_FINANCE,
                LogChannel.SESAME_CREDIT -> channel
                else -> LogChannel.COMMON
            }
        }

        private fun logByFlag(flag: String, msg: String) {
            when (resolveLogChannel(flag)) {
                LogChannel.FARM -> Log.farm(msg)
                LogChannel.FOREST -> Log.forest(msg)
                LogChannel.ORCHARD -> Log.orchard(msg)
                LogChannel.STALL -> Log.stall(msg)
                LogChannel.OCEAN -> Log.ocean(msg)
                LogChannel.MEMBER -> Log.member(msg)
                LogChannel.SPORTS -> Log.sports(msg)
                LogChannel.GREEN_FINANCE -> Log.greenFinance(msg)
                LogChannel.SESAME_CREDIT -> Log.sesame(msg)
                else -> Log.common(msg)
            }
        }

        @JvmStatic
        fun getAnswer(text: String?, answerList: List<String>?, flag: String): String {
            if (text == null || answerList == null) {
                logByFlag(flag, "问题或答案列表为空")
                return ""
            }
            var answerStr = ""
            try {
                val msg = String.format(QUESTION_LOG_FORMAT, text, answerList)
                logByFlag(flag, msg)
                
                if (enable && answerAIInterface != null) {
                    val answer = answerAIInterface?.getAnswer(text, answerList)
                    if (answer != null && answer >= 0 && answer < answerList.size) {
                        answerStr = answerList[answer]
                        val logMsg = String.format(
                            AI_ANSWER_LOG_FORMAT,
                            answerStr,
                            AIType.nickNames[getSafeAiType()],
                            answerAIInterface?.getModelName() ?: ""
                        )
                        logByFlag(flag, logMsg)
                    } else {
                        Log.error(ERROR_AI_ANSWER)
                    }
                } else if (answerList.isNotEmpty()) {
                    answerStr = answerList[0]
                    val logMsg = String.format(NORMAL_ANSWER_LOG_FORMAT, answerStr)
                    logByFlag(flag, logMsg)
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "AI获取答案异常:", t)
            }
            return answerStr
        }
    }
}
