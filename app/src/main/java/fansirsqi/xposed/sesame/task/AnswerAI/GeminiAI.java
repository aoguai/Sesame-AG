package fansirsqi.xposed.sesame.task.AnswerAI;

import static fansirsqi.xposed.sesame.util.JsonUtil.getValueByPath;

import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import fansirsqi.xposed.sesame.util.Log;
import lombok.Getter;
import lombok.Setter;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * GeminiAI帮助类，用于与Gemini接口交互以获取AI回答
 * 支持单条文本问题及带有候选答案列表的问题请求
 */
public class GeminiAI implements AnswerAIInterface {
    private static final String TAG = GeminiAI.class.getSimpleName();
    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String CONTENT_TYPE = "application/json";
    private static final String JSON_PATH = "candidates.[0].content.parts.[0].text";
    private static final String PREFIX = "只回答答案 ";
    private static final Integer TIME_OUT_SECONDS = 180;

    @Setter
    @Getter
    private String modelName = "gemini-2.5-flash";
    private final String token;

    public GeminiAI(String token) {
        this.token = token != null && !token.isEmpty() ? token : "";
    }

    // 移除控制字符
    private String removeControlCharacters(String text) {
        return text.replaceAll("\\p{Cntrl}&&[^\n" + "\t]", "");
    }

    /**
     * 构建请求体
     *
     * @param text 问题内容
     * @return 请求体的JSON字符串
     */
    private String buildRequestBody(String text) {
        text = removeControlCharacters(text);
        return String.format("{" + "\"contents\":[{" + "\"parts\":[{" + "\"text\":\"%s\"" + "}]" + "}]" + "}", PREFIX + text);
    }

    /**
     * 构建请求URL
     *
     * @return 完整的请求URL
     */
    private String buildRequestUrl() {
        return String.format("%s/v1beta/models/%s:generateContent?key=%s",
                BASE_URL, this.modelName, token);
    }

    @Override
    public String getAnswerStr(String text, String model) {
        setModelName(model);
        return getAnswerStr(text);
    }

    /**
     * 获取AI回答结果
     *
     * @param text 问题内容
     * @return AI回答结果
     */
    @Override
    public String getAnswerStr(String text) {
        String result = "";
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(TIME_OUT_SECONDS, TimeUnit.SECONDS)
                    .writeTimeout(TIME_OUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(TIME_OUT_SECONDS, TimeUnit.SECONDS)
                    .build();

            String content = buildRequestBody(text);
            MediaType mediaType = MediaType.parse(CONTENT_TYPE);
            RequestBody body = RequestBody.create(content, mediaType);
            String url = buildRequestUrl();
            Request request = new Request.Builder()
                    .url(url)
                    .method("POST", body)
                    .addHeader("Content-Type", CONTENT_TYPE)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.body() == null) {
                    return result;
                }
                String json = response.body().string();
                if (!response.isSuccessful()) {
                    Log.other("Gemini请求失败");
                    Log.record(TAG, "Gemini接口异常：" + json);
                    return result;
                }
                JSONObject jsonObject = new JSONObject(json);
                result = getValueByPath(jsonObject, JSON_PATH);
            }
        } catch (IOException | org.json.JSONException e) {
            Log.printStackTrace(TAG, e);
        }
        return result;
    }

    /**
     * 获取答案
     *
     * @param title      问题
     * @param answerList 答案集合
     * @return 空没有获取到
     */
    @Override
    public Integer getAnswer(String title, List<String> answerList) {
        try {
            StringBuilder answerStr = new StringBuilder();
            for (int i = 0; i < answerList.size(); i++) {
                answerStr.append(i + 1).append(".[")
                        .append(answerList.get(i)).append("]\n");
            }

            final String question = "问题：" + title + "\n\n" +
                    "答案列表：\n\n" + answerStr + "\n\n" +
                    "请只返回答案列表中的序号";

            // 同步调用，主线程等待结果
            String answerResult = getAnswerStr(question);

            if (answerResult != null && !answerResult.isEmpty()) {
                try {
                    int index = Integer.parseInt(answerResult.trim()) - 1;
                    if (index >= 0 && index < answerList.size()) {
                        return index;
                    }
                } catch (NumberFormatException e) {
                    // 如果不是纯数字，尝试模糊匹配答案内容
                    Log.other("AI🧠回答，非序号格式：" + answerResult);
                }

                // 模糊匹配答案内容
                for (int i = 0; i < answerList.size(); i++) {
                    if (answerResult.contains(answerList.get(i))) {
                        return i;
                    }
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
        return -1;
    }
}
