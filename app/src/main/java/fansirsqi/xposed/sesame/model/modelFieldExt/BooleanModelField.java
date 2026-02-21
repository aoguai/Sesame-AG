package fansirsqi.xposed.sesame.model.modelFieldExt;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import fansirsqi.xposed.sesame.R;
import fansirsqi.xposed.sesame.model.ModelField;
public class BooleanModelField extends ModelField<Boolean> {
  /**
   * 构造函数，初始化 BooleanModelField 对象
   *
   * @param code 字段代码
   * @param name 字段名称
   * @param value 字段初始值
   */
  public BooleanModelField(String code, String name, Boolean value) {
    super(code, name, value); // 调用父类构造函数
  }
  /**
   * 获取字段类型
   *
   * @return 字段类型字符串
   */
  @Override
  public String getType() {
    return "BOOLEAN"; // 返回字段类型
  }
  /**
   * 创建并返回 Switch 视图
   *
   * @param context 上下文对象
   * @return 生成的 Switch 视图
   */
  @Override
  public View getView(Context context) {
    @SuppressLint("UseSwitchCompatOrMaterialCode") Switch sw = new Switch(context); // 创建 Switch 控件
    sw.setText(getName()); // 设置 Switch 的文本为字段名称
    sw.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); // 设置布局参数
    sw.setMinHeight(150); // 设置最小高度
    sw.setMaxHeight(180); // 设置最大高度
    sw.setPaddingRelative(40, 0, 40, 0); // 设置左右内边距
    sw.setChecked(getValue()); // 根据字段值设置 Switch 的选中状态
    // 设置按钮和轨道样式
//    sw.setThumbResource(R.drawable.switch_thumb);
    sw.setTrackResource(R.drawable.switch_track);
    // 设置点击监听器，更新字段值
    sw.setOnClickListener(v -> setObjectValue(((Switch) v).isChecked()));
    return sw; // 返回创建的 Switch 视图
  }
}
