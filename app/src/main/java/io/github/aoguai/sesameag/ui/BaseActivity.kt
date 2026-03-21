package io.github.aoguai.sesameag.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import io.github.aoguai.sesameag.R

open class BaseActivity : AppCompatActivity() {

    // 🔥 修复点 1: 改为可空类型，不要使用 lateinit 或直接 lazy 非空
    // Compose 模式下，这个 Toolbar 可能根本不存在
    protected val toolbar: MaterialToolbar? by lazy {
        findViewById(R.id.x_toolbar)
    }
    // 暂存标题
    private var pendingTitle: CharSequence? = null
    private var pendingSubtitle: CharSequence? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }


    override fun onContentChanged() {
        super.onContentChanged()

        // 🔥 修复点 2: 安全访问 toolbar
        // 如果是 Compose 模式，findViewById 会返回 null，我们直接忽略即可
        toolbar?.let { tb ->
            setSupportActionBar(tb)
            tb.setContentInsetsAbsolute(0, 0)
            updateToolbarText()
        }
    }

    // 基础标题
    open var baseTitle: String?
        get() = pendingTitle?.toString()
        set(value) {
            pendingTitle = value
            updateToolbarText()
        }

    // 基础副标题
    open var baseSubtitle: String?
        get() = pendingSubtitle?.toString()
        set(value) {
            pendingSubtitle = value
            updateToolbarText()
        }

    private fun updateToolbarText() {
        // 🔥 修复点 3: 只有当 toolbar 存在时才更新
        toolbar?.let {
            it.title = pendingTitle
            it.subtitle = pendingSubtitle
        }
    }


//


}
