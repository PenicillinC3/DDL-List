package com.gammac3.ddlapp.widget

import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetManager
import android.content.Context

/**
 * 小组件接收器：三款（V1 单焦点卡 / V2 迷你章 / V3 二连行）共用同一条数据管线，
 * 各自只在自己的 appwidget-provider xml 上挂壳；任何一次更新调用都整批重算三款。
 */
open class DdlWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        DdlWidgets.refresh(context)
    }
}

class DdlWidgetV1Receiver : DdlWidgetReceiver()
class DdlWidgetV2Receiver : DdlWidgetReceiver()
class DdlWidgetV3Receiver : DdlWidgetReceiver()
