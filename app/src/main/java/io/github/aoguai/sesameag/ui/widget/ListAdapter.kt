package io.github.aoguai.sesameag.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.github.aoguai.sesameag.R
import io.github.aoguai.sesameag.entity.MapperEntity
import io.github.aoguai.sesameag.model.SelectModelFieldFunc
import io.github.aoguai.sesameag.util.Log

class ListAdapter private constructor(private val context: Context) : BaseAdapter() {

    private var list: List<MapperEntity>? = null
    private var selectModelFieldFunc: SelectModelFieldFunc? = null
    private var findIndex = -1
    private var findWord: String? = null

    fun setBaseList(l: List<MapperEntity>?) {
        if (l !== list) {
            exitFind()
        }
        this.list = l
    }

    fun setSelectedList(selectModelFieldFunc: SelectModelFieldFunc?) {
        this.selectModelFieldFunc = selectModelFieldFunc
        try {
            list?.let { list ->
                (list as? MutableList)?.sortWith { o1, o2 ->
                    val contains1 = selectModelFieldFunc?.contains(o1.id) == true
                    val contains2 = selectModelFieldFunc?.contains(o2.id) == true
                    when {
                        contains1 == contains2 -> o1.compareTo(o2)
                        contains1 -> -1
                        else -> 1
                    }
                }
            }
        } catch (e: Exception) {
            Log.runtime(TAG, "ListAdapter error")
            Log.printStackTrace(e)
        }
    }

    fun findLast(findThis: String): Int = findItem(findThis, false)

    fun findNext(findThis: String): Int = findItem(findThis, true)

    private fun findItem(findThis: String, forward: Boolean): Int {
        val currentList = list
        if (currentList == null || currentList.isEmpty()) {
            return -1
        }
        val findLower = findThis.lowercase()
        if (findLower != findWord) {
            resetFindState()
            findWord = findLower
        }
        var current = findIndex.coerceAtLeast(0)
        val size = currentList.size
        val start = current
        do {
            current = if (forward) (current + 1) % size else (current - 1 + size) % size
            if (currentList[current].name.lowercase().contains(findLower)) {
                findIndex = current
                notifyDataSetChanged()
                return findIndex
            }
        } while (current != start)
        return -1
    }

    fun resetFindState() {
        findIndex = -1
        findWord = null
    }

    fun exitFind() {
        resetFindState()
    }

    fun selectAll() {
        selectModelFieldFunc?.clear()
        list?.forEach { item ->
            selectModelFieldFunc?.add(item.id, 0)
        }
        notifyDataSetChanged()
    }

    fun SelectInvert() {
        list?.forEach { item ->
            if (selectModelFieldFunc?.contains(item.id) == false) {
                selectModelFieldFunc?.add(item.id, 0)
            } else {
                selectModelFieldFunc?.remove(item.id)
            }
        }
        notifyDataSetChanged()
    }

    override fun getCount(): Int = list?.size ?: 0

    override fun getItem(position: Int): Any? = list?.get(position)

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val vh: ViewHolder
        val view: View
        if (convertView == null) {
            vh = ViewHolder()
            view = View.inflate(context, R.layout.list_item, null)
            vh.tv = view.findViewById(R.id.tv_idn)
            vh.cb = view.findViewById(R.id.cb_list)
            if (listType == ListDialog.ListType.SHOW) {
                vh.cb.visibility = View.GONE
            }
            view.tag = vh
            viewHolderList.add(vh)
        } else {
            view = convertView
            vh = view.tag as ViewHolder
        }
        val item = list!![position]
        vh.tv.text = item.name
        val textColorPrimary = ContextCompat.getColor(context, R.color.textColorPrimary)
        vh.tv.setTextColor(if (findIndex == position) Color.RED else textColorPrimary)
        vh.cb.isChecked = selectModelFieldFunc?.contains(item.id) == true
        return view
    }

    class ViewHolder {
        lateinit var tv: TextView
        lateinit var cb: CheckBox
    }

    companion object {
        private const val TAG = "ListAdapter"
        
        @SuppressLint("StaticFieldLeak")
        private var adapter: ListAdapter? = null
        private var listType: ListDialog.ListType? = null
        
        @JvmField
        val viewHolderList = ArrayList<ViewHolder>()

        @JvmStatic
        fun get(c: Context): ListAdapter {
            if (adapter == null) {
                adapter = ListAdapter(c.applicationContext)
            }
            return adapter!!
        }

        @JvmStatic
        fun getClear(c: Context): ListAdapter {
            val adapter = get(c)
            adapter.resetFindState()
            return adapter
        }

        @JvmStatic
        fun getClear(c: Context, listType: ListDialog.ListType): ListAdapter {
            val adapter = get(c)
            Companion.listType = listType
            adapter.resetFindState()
            return adapter
        }
    }
}

