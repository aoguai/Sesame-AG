package fansirsqi.xposed.sesame.task;

import java.util.Arrays;
import java.util.List;

import fansirsqi.xposed.sesame.model.BaseModel;
import fansirsqi.xposed.sesame.model.Model;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.Job;

/**
 * TaskRunner适配器类
 * <p>
 * 为Java代码提供更友好的CoroutineTaskRunner调用方式
 * 适配了新的 suspend run 方法和 Job 管理机制
 */
public class TaskRunnerAdapter {

    private final CoroutineTaskRunner coroutineTaskRunner;

    // 用于追踪当前运行的任务 Job，以便执行 stop()
    private Job currentJob = null;

    /**
     * 构造函数 - 使用所有已注册的模型
     */
    public TaskRunnerAdapter() {
        List<Model> modelList = Arrays.asList(Model.getModelArray());
        this.coroutineTaskRunner = new CoroutineTaskRunner(modelList);
    }

    /**
     * 构造函数 - 使用指定的模型列表
     */
    public TaskRunnerAdapter(List<Model> models) {
        this.coroutineTaskRunner = new CoroutineTaskRunner(models);
    }

    /**
     * 执行任务 - 简化版本
     */
    public void run() {
        // Mode参数现在已废弃，新版Runner内部自动处理并发
        run(true, null);
    }

    /**
     * 执行任务 - 兼容旧接口
     * @param mode 该参数已被忽略，新版Runner使用内部并发控制
     */
    public void run(boolean isFirst, ModelTask.TaskExecutionMode mode) {
        run(isFirst, mode, BaseModel.Companion.getTaskExecutionRounds().getValue());
    }

    /**
     * 执行任务 - 包含轮数参数（主方法）
     */
    public void run(boolean isFirst, ModelTask.TaskExecutionMode mode, int rounds) {
        // 如果有旧任务在运行，先取消
        stop();

        // 使用 Kotlin 的 BuildersKt 在 Java 中启动协程
        // 相当于 Kotlin 的: currentJob = GlobalScope.launch(Dispatchers.Default) { runner.run(...) }
        this.currentJob = BuildersKt.launch(
                GlobalScope.INSTANCE, // 使用全局作用域，或者你可以传入一个自定义 Scope
                Dispatchers.getDefault(), // 在后台线程执行
                CoroutineStart.DEFAULT,
                (scope, continuation) -> {
                    // 调用 Kotlin 的 suspend 函数
                    return coroutineTaskRunner.run(isFirst, rounds, continuation);
                }
        );
    }

    /**
     * 停止任务执行器
     */
    public void stop() {
        if (currentJob != null && currentJob.isActive()) {
            currentJob.cancel(null); // 取消协程
            currentJob = null;
        }
    }

    /**
     * 静态方法：快速执行所有任务
     */
    public static void runAllTasks() {
        runAllTasks(null);
    }

    /**
     * 静态方法：使用指定模式执行所有任务
     */
    public static void runAllTasks(ModelTask.TaskExecutionMode mode) {
        new TaskRunnerAdapter().run(true, mode);
    }
}