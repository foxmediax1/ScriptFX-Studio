package net.foxmediax.app.ui

import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

object FileDialogs {

    fun pickMinecraftFolder(initial: File? = null): File? {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser(initial ?: File(System.getProperty("user.home")))
        chooser.dialogTitle = "Выберите папку Minecraft (.minecraft)"
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
            chooser.selectedFile
        else null
    }
}