package com.example.studio3d.core.history

import com.example.studio3d.core.math.*
import com.example.studio3d.core.engine.*
import com.example.studio3d.core.scene.*
import com.example.studio3d.core.history.*
import com.example.studio3d.core.preferences.*
import com.example.studio3d.core.project.*
import com.example.studio3d.feature.editor.viewport.*
import com.example.studio3d.feature.editor.tools.select.*


import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

class CommandHistory(private val maxHistory: Int = 50) {
    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _lastActionName = MutableStateFlow<String?>(null)
    val lastActionName: StateFlow<String?> = _lastActionName.asStateFlow()

    fun execute(command: Command) {
        command.execute()
        if (undoStack.size >= maxHistory) {
            undoStack.removeLast()
        }
        undoStack.push(command)
        redoStack.clear()
        _lastActionName.value = command.description
        updateState()
    }

    fun recordExecuted(command: Command) {
        if (undoStack.size >= maxHistory) {
            undoStack.removeLast()
        }
        undoStack.push(command)
        redoStack.clear()
        _lastActionName.value = command.description
        updateState()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val cmd = undoStack.pop()
        cmd.undo()
        redoStack.push(cmd)
        _lastActionName.value = "Undo: ${cmd.description}"
        updateState()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val cmd = redoStack.pop()
        cmd.execute()
        undoStack.push(cmd)
        _lastActionName.value = "Redo: ${cmd.description}"
        updateState()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        _lastActionName.value = null
        updateState()
    }

    private fun updateState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
}
