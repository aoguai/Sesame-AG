package fansirsqi.xposed.sesame.entity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import fansirsqi.xposed.sesame.util.Files;
import fansirsqi.xposed.sesame.util.Log;
/**
 * 区域代码类，继承自IdAndName。
 * 该类用于管理城市代码和城市名称。
 */
public class AreaCode extends MapperEntity {
    private static final String TAG = AreaCode.class.getSimpleName();
    private static List<AreaCode> list;
    /**
     * 构造函数，初始化区域代码对象。
     *
     * @param i 区域代码
     * @param n 区域名称
     */
    public AreaCode(String i, String n) {
        id = i;
        name = n;
    }
    /**
     * 获取区域代码列表。
     * 如果列表尚未初始化，则从文件中读取城市代码。
     * 如果读取失败，则使用默认城市代码。
     *
     * @return 区域代码列表
     */
    public static List<AreaCode> getList() throws JSONException {
        if (list == null) {
            String cityCode = Files.readFromFile(Files.getCityCodeFile());
            JSONArray ja = parseCityCode(cityCode);
            list = new ArrayList<>();
            for (int i = 0; i < ja.length(); i++) {
                try {
                    JSONObject jo = ja.getJSONObject(i);
                    list.add(new AreaCode(jo.getString("cityCode"), jo.getString("cityName")));
                } catch (JSONException e) {
                    Log.printStackTrace(TAG, e);
                }
            }
        }
        return list;
    }
    /**
     * 解析城市代码字符串为JSONArray。
     * 如果解析失败，则返回默认的城市代码JSONArray。
     *
     * @param cityCode 城市代码字符串
     * @return 解析后的JSONArray
     */
    private static JSONArray parseCityCode(String cityCode) throws JSONException {
        try {
            return new JSONArray(cityCode);
        } catch (JSONException e) {
            // 解析失败，使用默认城市代码
            Log.record(TAG, "parseCityCode failed with error message: " + e.getMessage()+"\n Now use default cities.");
            JSONArray defaultCities = new JSONArray();
            defaultCities.put(new JSONObject().put("cityCode", "350100").put("cityName", "福州市"));
            defaultCities.put(new JSONObject().put("cityCode", "440100").put("cityName", "广州市"));
            defaultCities.put(new JSONObject().put("cityCode", "330100").put("cityName", "杭州市"));
            defaultCities.put(new JSONObject().put("cityCode", "370100").put("cityName", "济南市"));
            defaultCities.put(new JSONObject().put("cityCode", "320100").put("cityName", "南京市"));
            defaultCities.put(new JSONObject().put("cityCode", "430100").put("cityName", "长沙市"));
            return defaultCities;
        }
    }
}