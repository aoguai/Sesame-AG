package io.github.aoguai.sesameag.ui

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.aoguai.sesameag.R
import io.github.aoguai.sesameag.model.ModelField
import io.github.aoguai.sesameag.util.Log

/**
 * 字符串对话框工具类。
 * 提供了显示编辑对话框和读取对话框的静态方法。
 */
object StringDialog {
    
    private var modelField: ModelField<*>? = null

    @JvmStatic
    @JvmOverloads
    fun showEditDialog(
        context: Context,
        title: CharSequence,
        modelField: ModelField<*>,
        msg: String? = null
    ) {
        this.modelField = modelField
        val editDialog = getEditDialog(context)
        msg?.let { editDialog.setMessage(it) }
        editDialog.setTitle(title)
        editDialog.show()
    }

    private fun getEditDialog(context: Context): AlertDialog {
        val edt = EditText(context)
        
        val editDialog = MaterialAlertDialogBuilder(context)
            .setTitle("title")
            .setView(edt)
            .setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                try {
                    val text = edt.text
                    if (text == null || text.toString().isEmpty()) {
                        modelField?.setConfigValue(null)
                    } else {
                        modelField?.setConfigValue(text.toString())
                    }
                } catch (e: Throwable) {
                    Log.printStackTrace(e)
                    // 显示错误提示给用户
                    android.widget.Toast.makeText(
                        context,
                        "保存失败: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton(context.getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        editDialog.setOnShowListener {
            val positiveButton = editDialog.getButton(DialogInterface.BUTTON_POSITIVE)
            positiveButton?.setTextColor(
                ContextCompat.getColor(context, R.color.selection_color)
            )
        }

        // 调用getConfigValue()方法而不是直接访问configValue属性
        edt.setText(modelField?.configValue)
        return editDialog
    }

    @JvmStatic
    @JvmOverloads
    fun showReadDialog(
        context: Context,
        title: CharSequence,
        modelField: ModelField<*>,
        msg: String? = null
    ) {
        this.modelField = modelField
        val readDialog = getReadDialog(context)
        msg?.let { readDialog.setMessage(it) }
        readDialog.setTitle(title)
        readDialog.show()
    }

    private fun getReadDialog(context: Context): AlertDialog {
        val edt = EditText(context).apply {
            inputType = InputType.TYPE_NULL
            setTextColor(Color.GRAY)
            setText(modelField?.configValue)
        }
        
        return MaterialAlertDialogBuilder(context)
            .setTitle("读取")
            .setView(edt)
            .setPositiveButton(context.getString(R.string.ok)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }

    @JvmStatic
    @JvmOverloads
    fun showAlertDialog(
        context: Context,
        title: String,
        msg: String,
        positiveButton: String = "确定"
    ) {
        val parsedMsg = HtmlCompat.fromHtml(msg, HtmlCompat.FROM_HTML_MODE_LEGACY)

        val alertDialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(parsedMsg)
            .setPositiveButton(positiveButton) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        alertDialog.show()

        val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
        button?.setTextColor(
            ContextCompat.getColor(context, R.color.textColorPrimary)
        )
    }

    @JvmStatic
    fun showSelectionDialog(
        context: Context,
        title: String,
        items: Array<CharSequence>,
        onItemClick: DialogInterface.OnClickListener,
        positiveButton: String,
        onDismiss: DialogInterface.OnDismissListener
    ): AlertDialog {
        val alertDialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setItems(items, onItemClick)
            .setOnDismissListener(onDismiss)
            .setPositiveButton(positiveButton) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        alertDialog.show()

        val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
        button?.setTextColor(
            ContextCompat.getColor(context, R.color.selection_color)
        )

        return alertDialog
    }
}

