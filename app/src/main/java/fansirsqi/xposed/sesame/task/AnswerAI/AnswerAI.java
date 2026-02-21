package fansirsqi.xposed.sesame.task.AnswerAI;

import java.util.List;

import fansirsqi.xposed.sesame.model.Model;
import fansirsqi.xposed.sesame.model.ModelFields;
import fansirsqi.xposed.sesame.model.ModelGroup;
import fansirsqi.xposed.sesame.model.modelFieldExt.ChoiceModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.StringModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.TextModelField;
import fansirsqi.xposed.sesame.util.Log;

public class AnswerAI extends Model {
    private static final String TAG = AnswerAI.class.getSimpleName();
    private static final String QUESTION_LOG_FORMAT = "题目📒 [%s] | 选项: %s";
    private static final String AI_ANSWER_LOG_FORMAT = "AI回答🧠 [%s] | AI类型: [%s] | 模型名称: [%s]";
    private static final String NORMAL_ANSWER_LOG_FORMAT = "普通回答🤖 [%s]";
    private static final String ERROR_AI_ANSWER = "AI回答异常：无法获取有效答案，请检查AI服务配置是否正确";

    private static Boolean enable = false;
    private static AnswerAIInterface answerAIInterface = AnswerAIInterface.getInstance();

    @Override
    public String getName() {
        return "AI答题";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    @Override
    public String getIcon() {
        return "AnswerAI.svg";
    }

    public interface AIType {
        int TONGYI = 0;
        int GEMINI = 1;
        int DEEPSEEK = 2;
        int CUSTOM = 3;

        String[] nickNames = {
                "通义千问",
                "Gemini",
                "DeepSeek",
                "自定义"
        };
    }

    private static final ChoiceModelField aiType = new ChoiceModelField("useGeminiAI", "AI类型", AIType.TONGYI, AIType.nickNames);
    private final TextModelField.UrlTextModelField getTongyiAIToken = new TextModelField.UrlTextModelField("getTongyiAIToken", "通义千问 | 获取令牌", "https://help.aliyun.com/zh/dashscope/developer-reference/acquisition-and-configuration-of-api-key");
    private final StringModelField tongYiToken = new StringModelField("tongYiToken", "qwen-turbo | 设置令牌", "");
    private final TextModelField.UrlTextModelField getGeminiAIToken = new TextModelField.UrlTextModelField("getGeminiAIToken", "Gemini | 获取令牌", "https://aistudio.google.com/app/apikey");
    private final StringModelField GeminiToken = new StringModelField("GeminiAIToken", "gemini-1.5-flash | 设置令牌", "");
    private final TextModelField.UrlTextModelField getDeepSeekToken = new TextModelField.UrlTextModelField("getDeepSeekToken", "DeepSeek | 获取令牌", "https://platform.deepseek.com/usage");
    private final StringModelField DeepSeekToken = new StringModelField("DeepSeekToken", "DeepSeek-R1 | 设置令牌", "");
    private final TextModelField.ReadOnlyTextModelField getCustomServiceToken = new TextModelField.ReadOnlyTextModelField(
            "getCustomServiceToken",
            "提示",
            "请自行填写 Token/Key（不要提交到仓库）"
    );

    private final StringModelField CustomServiceToken = new StringModelField("CustomServiceToken", "自定义服务 | 设置令牌", "");
    private final StringModelField CustomServiceUrl = new StringModelField("CustomServiceBaseUrl", "自定义服务 | 设置BaseUrl", "https://api.siliconflow.cn/v1");
    private final StringModelField CustomServiceModel = new StringModelField("CustomServiceModel", "自定义服务 | 设置模型", "deepseek-ai/DeepSeek-V3");

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(aiType);
        modelFields.addField(getTongyiAIToken);
        modelFields.addField(tongYiToken);
        modelFields.addField(getGeminiAIToken);
        modelFields.addField(GeminiToken);
        modelFields.addField(getDeepSeekToken);
        modelFields.addField(DeepSeekToken);
        modelFields.addField(getCustomServiceToken);
        modelFields.addField(CustomServiceToken);
        modelFields.addField(CustomServiceUrl);
        modelFields.addField(CustomServiceModel);
        return modelFields;
    }

    @Override
    public void boot(ClassLoader classLoader) {
        try {
            enable = getEnableField().getValue();
            int selectedType = aiType.getValue();
            Log.record(String.format("初始化AI服务：已选择[%s]", AIType.nickNames[selectedType]));
            initializeAIService(selectedType);
        } catch (Exception e) {
            Log.error(TAG, "初始化AI服务失败: " + e.getMessage());
            Log.printStackTrace(TAG, e);
        }
    }

    private void initializeAIService(int selectedType) {
        // 先释放旧的服务资源
        if (answerAIInterface != null) {
            answerAIInterface.release();
        }

        switch (selectedType) {
            case AIType.TONGYI:
                answerAIInterface = new TongyiAI(tongYiToken.getValue());
                break;
            case AIType.GEMINI:
                answerAIInterface = new GeminiAI(GeminiToken.getValue());
                break;
            case AIType.DEEPSEEK:
                answerAIInterface = new DeepSeek(DeepSeekToken.getValue());
                break;
            case AIType.CUSTOM:
                answerAIInterface = new CustomService(CustomServiceToken.getValue(), CustomServiceUrl.getValue());
                answerAIInterface.setModelName(CustomServiceModel.getValue());
                Log.record(String.format("已配置自定义服务：URL=[%s], Model=[%s]", CustomServiceUrl.getValue(), CustomServiceModel.getValue()));
                break;
            default:
                answerAIInterface = AnswerAIInterface.getInstance();
                break;
        }
    }

    private static void selectloger(String flag, String msg) {
        switch (flag) {
            case "farm":
                Log.farm(msg);
                break;
            case "forest":
                Log.forest(msg);
                break;
            default:
                Log.other(msg);
                break;
        }
    }

    /**
     *  AI 获取答案
     * @param text 问题
     * @param answerList 答案列表
     * @param flag 日志类型
     * @return 答案
     */
    public static String getAnswer(String text, List<String> answerList, String flag) {
        if (text == null || answerList == null) {
            selectloger(flag, "问题或答案列表为空");
            return "";
        }
        String answerStr = "";
        try {
            String msg = String.format(QUESTION_LOG_FORMAT, text, answerList);
            selectloger(flag, msg);
            if (enable && answerAIInterface != null) {
                Integer answer = answerAIInterface.getAnswer(text, answerList);
                if (answer != null && answer >= 0 && answer < answerList.size()) {
                    answerStr = answerList.get(answer);
                    selectloger(flag, String.format(AI_ANSWER_LOG_FORMAT, answerStr, AIType.nickNames[aiType.getValue()], answerAIInterface.getModelName()));
                } else {
                    Log.error(ERROR_AI_ANSWER);
                }
            } else if (!answerList.isEmpty()) {
                answerStr = answerList.get(0);
                selectloger(flag, String.format(NORMAL_ANSWER_LOG_FORMAT, answerStr));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "AI获取答案异常:", t);
        }
        return answerStr;
    }


}
