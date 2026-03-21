package io.github.aoguai.sesameag.task

/**
 * ChildTaskExecutor 接口用于定义管理子任务的方法。
 * 通过此接口可以对子任务进行添加、移除、清除操作。
 */
interface ChildTaskExecutor {
    
    /**
     * 添加一个子任务到任务执行者
     *
     * @param childTask 要添加的子任务
     * @return 如果添加成功返回 true，否则返回 false
     */
    fun addChildTask(childTask: ModelTask.ChildModelTask): Boolean

    /**
     * 移除一个子任务
     *
     * @param childTask 要移除的子任务
     */
    fun removeChildTask(childTask: ModelTask.ChildModelTask)

    /**
     * 清除指定组的所有子任务
     *
     * @param group 子任务所在的组
     * @return 如果清除成功返回 true，否则返回 false
     */
    fun clearGroupChildTask(group: String): Boolean

    /**
     * 清除所有子任务
     */
    fun clearAllChildTask()
}

