package fansirsqi.xposed.sesame.ui.adapter;
/*
 * @Description: 好友统计，列表长按菜单
 * @UpdateDate: 2024/10/23
 * @UpdateTime: 16:39
 */
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.ArrayList;
/**
 * 选项适配器。
 * 用于在列表视图中显示选项。
 */
public class OptionsAdapter extends BaseAdapter {
    @SuppressLint("StaticFieldLeak")
    private static OptionsAdapter adapter;
    /**
     * 获取单例适配器实例。
     * @param c 上下文对象。
     * @return 适配器实例。
     */
    public static OptionsAdapter get(Context c) {
        if (adapter == null) {
            adapter = new OptionsAdapter(c);
        }
        return adapter;
    }
    private final Context context;
    private final ArrayList<String> list;
    /**
     * 私有构造函数，防止外部直接实例化。
     * @param c 上下文对象。
     */
    private OptionsAdapter(Context c) {
        context = c;
        list = new ArrayList<>();
        // 初始化列表项
        list.add("查看森林");
        list.add("查看庄园");
        list.add("查看资料");
        list.add("删除");
    }
    @Override
    public int getCount() {
        // 返回列表项的数量
        return list == null ? 0 : list.size();
    }
    @Override
    public Object getItem(int position) {
        // 返回指定位置的列表项
        return list.get(position);
    }
    @Override
    public long getItemId(int position) {
        // 返回列表项的唯一ID，这里简单地使用位置作为ID
        return position;
    }
    @SuppressLint("InflateParams")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // 复用convertView以提高性能
        if (convertView == null) {
            // inflate布局
            convertView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, null);
        }
        // 获取TextView并设置文本
        TextView txt = (TextView) convertView;
        txt.setText(getItem(position).toString());
        return convertView;
    }
}