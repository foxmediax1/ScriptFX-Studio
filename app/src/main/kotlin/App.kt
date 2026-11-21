package net.foxmediax.app

import net.foxmediax.utils.Printer

//TIP Чтобы <b>запустить</b> код, нажмите <shortcut actionId="Run"/> или
// нажмите на значок <icon src="AllIcons.Actions.Execute"/> на полях.
fun main() {
    val name = "Kotlin"
    //TIP Нажмите <shortcut actionId="ShowIntentionActions"/>, когда курсор находится на подсвеченном тексте,
    // чтобы увидеть предложения IntelliJ IDEA по исправлению.
    val message = "Hello, " + name + "!"
    val printer = Printer(message)
    printer.printMessage()

    for (i in 1..5) {
        //TIP Нажмите <shortcut actionId="Debug"/> для начала отладки кода. Мы установили одну <icon src="AllIcons.Debugger.Db_set_breakpoint"/> точку останова
        // для вас, но вы всегда можете добавить больше, нажав <shortcut actionId="ToggleLineBreakpoint"/>.
        println("i = $i")
    }
}
