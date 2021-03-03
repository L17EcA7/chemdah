package ink.ptms.chemdah.core.quest.objective

import ink.ptms.chemdah.core.PlayerProfile
import ink.ptms.chemdah.core.quest.Task
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass

/**
 * Chemdah
 * ink.ptms.chemdah.core.quest.objective.Objective
 *
 * @author sky
 * @since 2021/3/1 11:52 下午
 */
@Suppress("UNCHECKED_CAST")
abstract class Objective<E : Event> {

    /**
     * 条目继续的条件
     */
    private val conditions = ArrayList<(PlayerProfile, Task, E) -> Boolean>()

    /**
     * 条目完成的条件
     */
    private val goals = ArrayList<(PlayerProfile, Task) -> Boolean>()

    /**
     * 条件序号
     */
    abstract val name: String

    /**
     * 事件类型
     */
    abstract val event: KClass<E>

    /**
     * 事件优先级
     */
    open val priority = EventPriority.HIGHEST

    /**
     * 事件忽略已取消
     */
    open val ignoreCancelled = true

    /**
     * 获取事件中的玩家
     */
    var handler: ((E) -> Player?) = { null }
        private set

    /**
     * 获取事件中的玩家
     * 内部方法
     */
    protected fun handler(handle: E.() -> Player?) {
        this.handler = handle
    }

    /**
     * 当条目继续时
     */
    abstract fun onContinue(profile: PlayerProfile, task: Task, event: Event)

    /**
     * 当条目完成时
     */
    open fun onComplete(profile: PlayerProfile, task: Task) {
        setCompletedSignature(profile, task, true)
    }

    /**
     * 当条目重置时
     */
    open fun onReset(profile: PlayerProfile, task: Task) {
        profile.dataOperator(task) {
            clear()
        }
    }

    /**
     * 添加条目继续的条件
     */
    open fun addCondition(func: (PlayerProfile, Task, E) -> Boolean) {
        conditions += func
    }

    /**
     * 检查条目继续的所有条件
     * 当所有条件满足时再检查脚本代理
     */
    open fun checkCondition(profile: PlayerProfile, task: Task, event: E): CompletableFuture<Boolean> {
        return if (conditions.all { it(profile, task, event) }) {
            profile.checkAgent(task.condition["$"]?.value, event)
        } else {
            CompletableFuture.completedFuture(false)
        }
    }

    /**
     * 添加条目完成的条件
     */
    open fun addGoal(func: (PlayerProfile, Task) -> Boolean) {
        goals += func
    }

    /**
     * 检查条目完成的所有条件
     * 当所有条件满足时再检查脚本代理
     *
     * 当 "completed" 为 "true" 则强制判定为已完成
     */
    open fun checkGoal(profile: PlayerProfile, task: Task): CompletableFuture<Boolean> {
        return when {
            hasCompletedSignature(profile, task) -> CompletableFuture.completedFuture(false)
            goals.all { it(profile, task) } -> profile.checkAgent(task.goal["$"]?.value)
            else -> CompletableFuture.completedFuture(false)
        }
    }

    /**
     * 检查条目完成的所有条件
     * 当条件满足时则完成条目
     */
    open fun checkComplete(profile: PlayerProfile, task: Task) {
        if (!hasCompletedSignature(profile, task)) {
            checkGoal(profile, task).thenAccept {
                if (it && !hasCompletedSignature(profile, task)) {
                    onComplete(profile, task)
                }
            }
        }
    }

    /**
     * 设置目标为已完成状态
     */
    open fun setCompletedSignature(profile: PlayerProfile, task: Task, value: Boolean) {
        profile.dataOperator(task) {
            put("completed", value)
        }
    }

    /**
     * 检测目标是否为已完成状态
     */
    open fun hasCompletedSignature(profile: PlayerProfile, task: Task): Boolean {
        return profile.dataOperator(task) {
            get("completed", false).toBoolean()
        }
    }

    /**
     * 获取条目进度
     */
    abstract fun getProgress(profile: PlayerProfile, task: Task): Progress
}