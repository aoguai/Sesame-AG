package io.github.aoguai.sesameag.task.AnswerAI

import io.github.aoguai.sesameag.model.Model
import io.github.aoguai.sesameag.model.ModelFields
import io.github.aoguai.sesameag.model.ModelGroup
import io.github.aoguai.sesameag.model.withDesc
import io.github.aoguai.sesameag.model.modelFieldExt.ChoiceModelField
import io.github.aoguai.sesameag.model.modelFieldExt.StringModelField
import io.github.aoguai.sesameag.model.modelFieldExt.TextModelField
import io.github.aoguai.sesameag.util.Log

class AnswerAI : Model() {

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

    private val getTongyiAIToken = TextModelField.UrlTextModelField(
        "getTongyiAIToken",
        "通义千问 | 获取令牌",
        "https://help.aliyun.com/zh/dashscope/developer-reference/acquisition-and-configuration-of-api-key"
    ).withDesc("打开通义千问官方文档查看 API Key 的申请与配置方式，仅在下方 AI 类型选择通义千问时使用。")
    private val tongYiToken = StringModelField("tongYiToken", "qwen-turbo | 设置令牌", "").withDesc(
        "填写通义千问的 DashScope API Key；未填写或失效时无法使用通义千问答题。"
    )
    private val getGeminiAIToken = TextModelField.UrlTextModelField(
        "getGeminiAIToken",
        "Gemini | 获取令牌",
        "https://aistudio.google.com/app/apikey"
    ).withDesc("打开 Gemini 官方密钥页面获取 API Key，仅在下方 AI 类型选择 Gemini 时使用。")
    private val GeminiToken = StringModelField("GeminiAIToken", "gemini-1.5-flash | 设置令牌", "").withDesc(
        "填写 Gemini API Key；用于调用 gemini-1.5-flash 模型进行答题。"
    )
    private val getDeepSeekToken = TextModelField.UrlTextModelField(
        "getDeepSeekToken",
        "DeepSeek | 获取令牌",
        "https://platform.deepseek.com/usage"
    ).withDesc("打开 DeepSeek 开放平台查看 API Key 获取方式，仅在下方 AI 类型选择 DeepSeek 时使用。")
    private val DeepSeekToken = StringModelField("DeepSeekToken", "DeepSeek-R1 | 设置令牌", "").withDesc(
        "填写 DeepSeek API Key；用于调用 DeepSeek-R1 模型进行答题。"
    )
    private val getCustomServiceToken = TextModelField.ReadOnlyTextModelField(
        "getCustomServiceToken",
        "粉丝福利😍",
        "下面这个不用动可以白嫖到3月10号让我们感谢讯飞大善人🙏"
    ).withDesc("仅作当前默认自定义服务的提示说明；如果你有自己的兼容服务，可直接改下面三项配置。")
    private val CustomServiceToken = StringModelField(
        "CustomServiceToken",
        "自定义服务 | 设置令牌",
        "sk-pQF9jek0CTTh3boKDcA9DdD7340a4e929eD00a13F681Cd8e"
    ).withDesc("填写兼容 OpenAI 接口的自定义服务令牌，仅在 AI 类型选择自定义服务时生效。")
    private val CustomServiceUrl = StringModelField(
        "CustomServiceBaseUrl",
        "自定义服务 | 设置BaseUrl",
        "https://maas-api.cn-huabei-1.xf-yun.com/v1"
    ).withDesc("填写自定义服务的接口根地址，通常形如 https://host/v1，仅在 AI 类型选择自定义服务时生效。")
    private val CustomServiceModel = StringModelField(
        "CustomServiceModel",
        "自定义服务 | 设置模型",
        "xdeepseekr1"
    ).withDesc("填写自定义服务实际使用的模型名称，仅在 AI 类型选择自定义服务时生效。")

    override fun getName(): String = "AI答题"

    override fun getGroup(): ModelGroup = ModelGroup.OTHER

    override fun getIcon(): String = "AnswerAI.svg"

    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(aiType)
        modelFields.addField(getTongyiAIToken)
        modelFields.addField(tongYiToken)
        modelFields.addField(getGeminiAIToken)
        modelFields.addField(GeminiToken)
        modelFields.addField(getDeepSeekToken)
        modelFields.addField(DeepSeekToken)
        modelFields.addField(getCustomServiceToken)
        modelFields.addField(CustomServiceToken)
        modelFields.addField(CustomServiceUrl)
        modelFields.addField(CustomServiceModel)
        return modelFields
    }

    override fun prepare() {
        enable = enableField.value == true
        if (!enable) {
            disableAIService()
        }
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

    private fun selectlogger(flag: String, msg: String) {
        when (flag) {
            "farm" -> Log.farm(msg)
            "forest" -> Log.forest(msg)
            else -> Log.other(msg)
        }
    }

    companion object {
        private val TAG = AnswerAI::class.java.simpleName
        private const val QUESTION_LOG_FORMAT = "题目📒 [%s] | 选项: %s"
        private const val AI_ANSWER_LOG_FORMAT = "AI回答🧠 [%s] | AI类型: [%s] | 模型名称: [%s]"
        private const val NORMAL_ANSWER_LOG_FORMAT = "普通回答🤖 [%s]"
        private const val ERROR_AI_ANSWER = "AI回答异常：无法获取有效答案，请检查AI服务配置是否正确"

        private var enable = false
        private var answerAIInterface: AnswerAIInterface? = null
        private val aiType = ChoiceModelField("useGeminiAI", "AI类型", AIType.TONGYI, AIType.nickNames).withDesc(
            "选择当前用于自动答题的 AI 服务；关闭模块总开关后会退回普通答题逻辑。"
        )

        private fun getSafeAiType(): Int {
            return (aiType.value ?: AIType.TONGYI).coerceIn(0, AIType.nickNames.lastIndex)
        }

        @JvmStatic
        fun getAnswer(text: String?, answerList: List<String>?, flag: String): String {
            if (text == null || answerList == null) {
                when (flag) {
                    "farm" -> Log.farm("问题或答案列表为空")
                    "forest" -> Log.forest("问题或答案列表为空")
                    else -> Log.other("问题或答案列表为空")
                }
                return ""
            }
            var answerStr = ""
            try {
                val msg = String.format(QUESTION_LOG_FORMAT, text, answerList)
                when (flag) {
                    "farm" -> Log.farm(msg)
                    "forest" -> Log.forest(msg)
                    else -> Log.other(msg)
                }
                
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
                        when (flag) {
                            "farm" -> Log.farm(logMsg)
                            "forest" -> Log.forest(logMsg)
                            else -> Log.other(logMsg)
                        }
                    } else {
                        Log.error(ERROR_AI_ANSWER)
                    }
                } else if (answerList.isNotEmpty()) {
                    answerStr = answerList[0]
                    val logMsg = String.format(NORMAL_ANSWER_LOG_FORMAT, answerStr)
                    when (flag) {
                        "farm" -> Log.farm(logMsg)
                        "forest" -> Log.forest(logMsg)
                        else -> Log.other(logMsg)
                    }
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "AI获取答案异常:", t)
            }
            return answerStr
        }
    }
}

