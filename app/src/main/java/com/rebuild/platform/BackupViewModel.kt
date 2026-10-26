package com.rebuild.platform

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rebuild.RebuildApplication
import com.rebuild.domain.Action
import com.rebuild.domain.AppRepository
import com.rebuild.domain.BackupDocument
import com.rebuild.domain.BackupPreview
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val messageTitle: String = "Backup",
    val preview: BackupPreview? = null,
    val safetyRevision: Long? = null
)

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RebuildApplication
    private val _repository = MutableStateFlow<AppRepository?>(null)
    val repository = _repository.asStateFlow()
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()
    private val _ui = MutableStateFlow(BackupUiState())
    val ui = _ui.asStateFlow()
    private var export: BackupDocument? = null
    private var safetyExport = false

    init { load() }

    fun load() {
        _loadError.value = null
        viewModelScope.launch {
            try {
                _repository.value = app.repository()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e("Rebuild", "Cannot open saved workouts", error)
                _loadError.value = "Saved data could not be opened. Nothing was reset. ${error.message.orEmpty()}"
            }
        }
    }

    fun beginExport(safety: Boolean, launchPicker: (String) -> Unit) = operation {
        check(export == null) { "Finish or cancel the current export picker first." }
        val repository = requireNotNull(_repository.value) { "Saved data is not ready." }
        require(!safety || _ui.value.preview != null) { "Choose a restore file first." }
        val document = repository.exportBackup()
        try {
            export = document
            safetyExport = safety
            launchPicker("rebuild-${if (safety) "before-restore-" else ""}${System.currentTimeMillis()}.json")
        } catch (error: RuntimeException) {
            export = null
            safetyExport = false
            throw error
        }
    }

    fun completeExport(uri: Uri?) {
        val document = export
        val wasSafety = safetyExport
        export = null
        safetyExport = false
        if (uri == null) {
            _ui.value = _ui.value.copy(message = "Export canceled. Existing data is unchanged.", messageTitle = "Export canceled")
            return
        }
        if (document == null) {
            _ui.value = _ui.value.copy(message = "The export request was interrupted. Please export again; existing data is unchanged.")
            return
        }
        operation {
            withContext(Dispatchers.IO) {
                val resolver = app.contentResolver
                val output = resolver.openOutputStream(uri, "wt")
                    ?: throw IOException("The selected location cannot be written.")
                output.use { stream ->
                    stream.write(document.json.toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
            }
            requireNotNull(_repository.value).execute(Action.MarkBackup(document.revision, System.currentTimeMillis()))
            _ui.value = if (wasSafety) {
                _ui.value.copy(safetyRevision = document.revision, message = null)
            } else {
                _ui.value.copy(message = "Backup saved. Keep this private file somewhere you control.", messageTitle = "Backup saved")
            }
        }
    }

    fun readRestore(uri: Uri?) {
        if (uri == null) return
        operation {
            val text = withContext(Dispatchers.IO) {
                val input = app.contentResolver.openInputStream(uri)
                    ?: throw IOException("The selected file cannot be read.")
                input.use { stream ->
                    val bytes = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count == -1) break
                        require(bytes.size() + count <= MAX_BACKUP_BYTES) { "Backup exceeds the 10 MiB import limit." }
                        bytes.write(buffer, 0, count)
                    }
                    Charsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
                }
            }
            val preview = withContext(Dispatchers.Default) {
                requireNotNull(_repository.value).previewBackup(text)
            }
            _ui.value = BackupUiState(preview = preview)
        }
    }

    fun cancelRestore() {
        if (!_ui.value.busy) _ui.value = BackupUiState()
    }

    fun confirmRestore() = operation {
        val current = _ui.value
        val preview = requireNotNull(current.preview) { "Choose a restore file first." }
        val safetyRevision = requireNotNull(current.safetyRevision) { "Save a current-data backup first." }
        requireNotNull(_repository.value).restoreBackup(preview, safetyRevision)
        _ui.value = BackupUiState(message = "Backup restored. Data was replaced, not merged. Any active workout is ready to resume; old rest alerts were cleared.",
            messageTitle = "Restore complete")
    }

    fun prepareShare(share: (File) -> Unit) = operation {
        val document = requireNotNull(_repository.value).exportBackup()
        val file = withContext(Dispatchers.IO) {
            val directory = File(app.cacheDir, "shared-backups")
            if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create a private sharing folder.")
            directory.listFiles()?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - 86_400_000L }
                ?.forEach { old ->
                    if (!old.delete()) Log.w("Rebuild", "Could not remove expired shared backup: ${old.name}")
                }
            File(directory, "rebuild-${UUID.randomUUID()}.json").also { it.writeText(document.json) }
        }
        share(file)
        _ui.value = _ui.value.copy(
            message = "Share sheet opened. Rebuild cannot verify whether another app saved the file, so this does not clear the backup reminder. Use Save backup for a confirmed export.",
            messageTitle = "Share backup"
        )
    }

    fun dismissMessage() { _ui.value = _ui.value.copy(message = null) }

    fun showPlatformError(message: String) { _ui.value = _ui.value.copy(message = message, messageTitle = "Android settings") }

    private fun operation(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.value = _ui.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                block()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e("Rebuild", "Backup operation failed", error)
                _ui.value = _ui.value.copy(
                    message = "Operation failed. Existing workout data is unchanged. ${error.message ?: "Check file access and storage space."}",
                    messageTitle = "Backup operation failed",
                    safetyRevision = null
                )
            } finally {
                _ui.value = _ui.value.copy(busy = false)
            }
        }
    }

    companion object { const val MAX_BACKUP_BYTES = 10 * 1024 * 1024 }
}
