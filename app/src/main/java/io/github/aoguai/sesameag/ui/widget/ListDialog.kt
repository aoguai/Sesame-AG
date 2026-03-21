package io.github.aoguai.sesameag.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONException
import io.github.aoguai.sesameag.R
import io.github.aoguai.sesameag.entity.AreaCode
import io.github.aoguai.sesameag.entity.CooperateEntity
import io.github.aoguai.sesameag.entity.MapperEntity
import io.github.aoguai.sesameag.model.SelectModelFieldFunc
import io.github.aoguai.sesameag.model.modelFieldExt.SelectAndCountModelField
import io.github.aoguai.sesameag.model.modelFieldExt.SelectAndCountOneModelField
import io.github.aoguai.sesameag.model.modelFieldExt.SelectModelField
import io.github.aoguai.sesameag.model.modelFieldExt.SelectOneModelField
import io.github.aoguai.sesameag.ui.OptionsAdapter
import io.github.aoguai.sesameag.util.maps.CooperateMap

object ListDialog {
    private var listDialog: AlertDialog? = null
    
    @SuppressLint("StaticFieldLeak")
    private var btnFindLast: Button? = null
    @SuppressLint("StaticFieldLeak")
    private var btnFindNext: Button? = null
    @SuppressLint("StaticFieldLeak")
    private var btnSelectAll: Button? = null
    @SuppressLint("StaticFieldLeak")
    private var btnSelectInvert: Button? = null
    @SuppressLint("StaticFieldLeak")
    private var searchText: EditText? = null
    @SuppressLint("StaticFieldLeak")
    private var lvList: ListView? = null
    private var selectModelFieldFunc: SelectModelFieldFunc? = null
    private var hasCount: Boolean = false
    private var listType: ListType? = null
    @SuppressLint("StaticFieldLeak")
    private var layoutBatchProcess: RelativeLayout? = null

    enum class ListType {
        RADIO, CHECK, SHOW
    }

    @JvmStatic
    fun show(c: Context, title: CharSequence, selectModelField: SelectOneModelField, listType: ListType) {
        show(c, title, selectModelField.getExpandValue(), selectModelField, false, listType)
    }

    @JvmStatic
    fun show(c: Context, title: CharSequence, selectModelField: SelectAndCountOneModelField, listType: ListType) {
        show(c, title, selectModelField.getExpandValue(), selectModelField, false, listType)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun show(c: Context, title: CharSequence, selectModelField: SelectModelField) {
        show(c, title, selectModelField, ListType.CHECK)
    }

    @JvmStatic
    fun show(c: Context, title: CharSequence, selectModelField: SelectAndCountModelField) {
        show(c, title, selectModelField, ListType.CHECK)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun show(c: Context, title: CharSequence, selectModelField: SelectModelField, listType: ListType) {
        show(c, title, selectModelField.getExpandValue(), selectModelField, false, listType)
    }

    @JvmStatic
    fun show(c: Context, title: CharSequence, selectModelField: SelectAndCountModelField, listType: ListType) {
        show(c, title, selectModelField.getExpandValue(), selectModelField, true, listType)
    }

    @JvmStatic
    fun show(c: Context, title: CharSequence, bl: List<MapperEntity>?, selectModelFieldFunc: SelectModelFieldFunc?, hasCount: Boolean) {
        show(c, title, bl, selectModelFieldFunc, hasCount, ListType.CHECK)
    }

    @JvmStatic
    fun show(c: Context, title: CharSequence, bl: List<MapperEntity>?, selectModelFieldFunc: SelectModelFieldFunc?, hasCount: Boolean, listType: ListType) {
        this.selectModelFieldFunc = selectModelFieldFunc
        this.hasCount = hasCount
        val la = ListAdapter.getClear(c, listType)
        la.setBaseList(bl)
        la.setSelectedList(selectModelFieldFunc)
        showListDialog(c, title)
        this.listType = listType
    }

    private fun showListDialog(c: Context, title: CharSequence) {
        if (listDialog == null || listDialog?.context != c) {
            listDialog = MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setView(getListView(c))
                .setPositiveButton(c.getString(R.string.close), null)
                .create()
        }
        listDialog?.setOnShowListener { dialog ->
            val d = dialog as AlertDialog
            layoutBatchProcess = d.findViewById(R.id.layout_batch_process)
            layoutBatchProcess?.visibility = if (listType == ListType.CHECK && !hasCount) View.VISIBLE else View.GONE
            ListAdapter.get(c).notifyDataSetChanged()
        }
        listDialog?.show()
        val positiveButton = listDialog?.getButton(DialogInterface.BUTTON_POSITIVE)
        positiveButton?.setTextColor(ContextCompat.getColor(c, R.color.selection_color))
    }

    @SuppressLint("InflateParams")
    private fun getListView(c: Context): View {
        val v = LayoutInflater.from(c).inflate(R.layout.dialog_list, null)
        btnFindLast = v.findViewById(R.id.btn_find_last)
        btnFindNext = v.findViewById(R.id.btn_find_next)
        btnSelectAll = v.findViewById(R.id.btn_select_all)
        btnSelectInvert = v.findViewById(R.id.btn_select_invert)
        
        val onBtnClickListener = View.OnClickListener { v1 ->
            if (searchText?.length() ?: 0 <= 0) return@OnClickListener
            val la = ListAdapter.get(v1.context)
            val index = when (v1.id) {
                R.id.btn_find_last -> la.findLast(searchText?.text.toString())
                R.id.btn_find_next -> la.findNext(searchText?.text.toString())
                else -> -1
            }
            if (index < 0) {
                Toast.makeText(v1.context, "未搜到", Toast.LENGTH_SHORT).show()
            } else {
                lvList?.setSelection(index)
            }
        }
        btnFindLast?.setOnClickListener(onBtnClickListener)
        btnFindNext?.setOnClickListener(onBtnClickListener)

        val batchBtnOnClickListener = View.OnClickListener { v1 ->
            val la = ListAdapter.get(v1.context)
            when (v1.id) {
                R.id.btn_select_all -> la.selectAll()
                R.id.btn_select_invert -> la.SelectInvert()
            }
        }
        btnSelectAll?.setOnClickListener(batchBtnOnClickListener)
        btnSelectInvert?.setOnClickListener(batchBtnOnClickListener)

        searchText = v.findViewById(R.id.edt_find)
        lvList = v.findViewById(R.id.lv_list)
        lvList?.adapter = ListAdapter.getClear(c)

        lvList?.setOnItemClickListener { parent, view, position, _ ->
            if (listType == ListType.SHOW) return@setOnItemClickListener
            val cur = parent.adapter.getItem(position) as MapperEntity
            val holder = view.tag as ListAdapter.ViewHolder
            
            if (!hasCount) {
                if (listType == ListType.RADIO) {
                    selectModelFieldFunc?.clear()
                    if (holder.cb.isChecked) {
                        holder.cb.isChecked = false
                    } else {
                        ListAdapter.viewHolderList.forEach { it.cb.isChecked = false }
                        holder.cb.isChecked = true
                        selectModelFieldFunc?.add(cur.id, 0)
                    }
                } else {
                    if (holder.cb.isChecked) {
                        selectModelFieldFunc?.remove(cur.id)
                        holder.cb.isChecked = false
                    } else {
                        if (selectModelFieldFunc?.contains(cur.id) == false) {
                            selectModelFieldFunc?.add(cur.id, 0)
                        }
                        holder.cb.isChecked = true
                    }
                }
            } else {
                val edt = EditText(c)
                val edtDialog = MaterialAlertDialogBuilder(c)
                    .setTitle(cur.name)
                    .setView(edt)
                    .setPositiveButton(c.getString(R.string.ok)) { _, _ ->
                        if (edt.length() > 0) {
                            try {
                                val count = edt.text.toString().toInt()
                                if (count > 0) {
                                    selectModelFieldFunc?.add(cur.id, count)
                                    holder.cb.isChecked = true
                                } else {
                                    selectModelFieldFunc?.remove(cur.id)
                                    holder.cb.isChecked = false
                                }
                            } catch (ignored: Exception) {
                            }
                        }
                        ListAdapter.get(c).notifyDataSetChanged()
                    }
                    .setNegativeButton(c.getString(R.string.cancel), null)
                    .create()
                edt.hint = if (cur is CooperateEntity) "浇水克数" else "次数"
                val value = selectModelFieldFunc?.get(cur.id)
                if (value != null && value >= 0) {
                    edt.setText(value.toString())
                }
                edtDialog.show()
            }
        }

        lvList?.setOnItemLongClickListener { parent, view, position, _ ->
            val cur = parent.adapter.getItem(position) as MapperEntity
            when {
                cur is CooperateEntity -> {
                    MaterialAlertDialogBuilder(c)
                        .setTitle("删除 ${cur.name}")
                        .setPositiveButton(c.getString(R.string.ok)) { _, _ ->
                            io.github.aoguai.sesameag.util.maps.IdMapManager.getInstance(CooperateMap::class.java).remove(cur.id)
                            selectModelFieldFunc?.remove(cur.id)
                            ListAdapter.get(c).exitFind()
                            ListAdapter.get(c).notifyDataSetChanged()
                        }
                        .setNegativeButton(c.getString(R.string.cancel), null)
                        .show()
                }
                cur !is AreaCode -> {
                    MaterialAlertDialogBuilder(c)
                        .setTitle("选项")
                        .setAdapter(OptionsAdapter.get(c)) { _, which ->
                            val url = when (which) {
                                0 -> "alipays://platformapi/startapp?saId=10000007&qrcode=https%3A%2F%2F60000002.h5app.alipay.com%2Fwww%2Fhome.html%3FuserId%3D"
                                1 -> "alipays://platformapi/startapp?saId=10000007&qrcode=https%3A%2F%2F66666674.h5app.alipay.com%2Fwww%2Findex.htm%3Fuid%3D"
                                2 -> "alipays://platformapi/startapp?appId=20000166&actionType=profile&userId="
                                3 -> {
                                    MaterialAlertDialogBuilder(c)
                                        .setTitle("删除 ${cur.name}")
                                        .setPositiveButton(c.getString(R.string.ok)) { _, _ ->
                                            selectModelFieldFunc?.remove(cur.id)
                                            ListAdapter.get(c).exitFind()
                                            ListAdapter.get(c).notifyDataSetChanged()
                                        }
                                        .setNegativeButton(c.getString(R.string.cancel), null)
                                        .show()
                                    null
                                }
                                else -> null
                            }
                            url?.let {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it + cur.id))
                                c.startActivity(intent)
                            }
                        }
                        .setNegativeButton(c.getString(R.string.cancel), null)
                        .show()
                }
            }
            true
        }

        return v
    }
}

