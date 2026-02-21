package fansirsqi.xposed.sesame.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.entity.RpcDebugEntity

/**
 * RPC 调试项列表适配器
 */
class RpcDebugAdapter(
    private val items: MutableList<RpcDebugEntity>,
    private val onRun: (RpcDebugEntity) -> Unit,
    private val onEdit: (RpcDebugEntity) -> Unit,
    private val onDelete: (RpcDebugEntity) -> Unit,
    private val onCopy: (RpcDebugEntity) -> Unit
) : RecyclerView.Adapter<RpcDebugAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_rpc_name)
        val tvMethod: TextView = view.findViewById(R.id.tv_rpc_method)
        val btnRun: MaterialButton = view.findViewById(R.id.btn_run)
        val btnEdit: MaterialButton = view.findViewById(R.id.btn_edit)
        val btnDelete: MaterialButton = view.findViewById(R.id.btn_delete)
        val btnCopy: MaterialButton = view.findViewById(R.id.btn_copy)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rpc_debug, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.getDisplayName()
        holder.tvMethod.text = item.method
        holder.btnRun.setOnClickListener { onRun(item) }
        holder.btnEdit.setOnClickListener { onEdit(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
        holder.btnCopy.setOnClickListener { onCopy(item) }
        // 点击整行也可以运行
        holder.itemView.setOnClickListener { onRun(item) }
    }

    override fun getItemCount() = items.size

}