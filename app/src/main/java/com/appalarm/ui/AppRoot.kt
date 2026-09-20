package com.appalarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.appalarm.ui.bank.QuestionBankScreen
import com.appalarm.ui.bank.QuestionEditScreen
import com.appalarm.ui.edit.AlarmEditScreen
import com.appalarm.ui.home.HomeScreen
import com.appalarm.ui.settings.SettingsScreen

/**
 * 这个应用只有 5 个界面，用一个 `List<Screen>` 当返回栈就够了，
 * 不值得为它引入 Navigation Compose 和一套路由/参数序列化。
 */
sealed interface Screen {
    data object Home : Screen
    data class EditAlarm(val alarmId: String?) : Screen
    data object QuestionBank : Screen
    data class EditQuestion(val questionId: String?) : Screen
    data object Settings : Screen
}

@Composable
fun AppRoot() {
    var backStack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }

    fun push(screen: Screen) {
        backStack = backStack + screen
    }

    fun pop() {
        if (backStack.size > 1) backStack = backStack.dropLast(1)
    }

    BackHandler(enabled = backStack.size > 1) { pop() }

    when (val screen = backStack.last()) {
        Screen.Home -> HomeScreen(
            onAddAlarm = { push(Screen.EditAlarm(null)) },
            onEditAlarm = { push(Screen.EditAlarm(it)) },
            onOpenQuestionBank = { push(Screen.QuestionBank) },
            onOpenSettings = { push(Screen.Settings) },
        )

        is Screen.EditAlarm -> AlarmEditScreen(
            alarmId = screen.alarmId,
            onDone = { pop() },
        )

        Screen.QuestionBank -> QuestionBankScreen(
            onEditQuestion = { push(Screen.EditQuestion(it)) },
            onBack = { pop() },
        )

        is Screen.EditQuestion -> QuestionEditScreen(
            questionId = screen.questionId,
            onDone = { pop() },
        )

        Screen.Settings -> SettingsScreen(onBack = { pop() })
    }
}
