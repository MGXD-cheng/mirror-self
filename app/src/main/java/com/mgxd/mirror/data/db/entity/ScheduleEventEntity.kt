package com.mgxd.mirror.data.db.entity

/**
 * 拟人化日程事件实体。
 * 每个事件有一个"触发窗口"（windowStart ~ windowEnd），
 * 后台任务在窗口内推送一次通知。
 */
data class ScheduleEventEntity(
    var id: Long = 0L,

    var date: String = "",              // yyyy-MM-dd
    var weekday: Int = 1,               // 1=周一 ... 7=周日

    var title: String = "",             // 起床 / 出门 / 工作 / 午餐 ...
    var type: String = "OTHER",         // WAKE / OUT / WORK / MEAL / REST / FUN / SLEEP / SURPRISE

    var baseTime: String = "07:00",     // 基准时间
    var actualTime: String = "07:05",   // 本次实际生成时间（含随机偏移）
    var windowStart: String = "06:50",  // 触发窗口起点
    var windowEnd: String = "07:15",    // 触发窗口终点

    var isTriggered: Boolean = false,   // 是否已推送过通知
    var isManual: Boolean = false,      // 是否用户手动调整过
    var note: String = ""               // 附加说明（如硬性约束备注）
)