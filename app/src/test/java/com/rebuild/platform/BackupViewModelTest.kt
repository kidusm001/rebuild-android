package com.rebuild.platform

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.rebuild.RebuildApplication
import com.rebuild.domain.Action
import com.rebuild.domain.AppRepository
import com.rebuild.domain.Phase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RebuildApplication::class)
class BackupViewModelTest {
    private val store = ViewModelStore()
    private lateinit var model: BackupViewModel
    private lateinit var app: RebuildApplication

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        model = BackupViewModel(app)
        store.put("backup", model)
    }

    @After fun cleanup() {
        store.clear()
        app.applicationScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        Dispatchers.resetMain()
    }

    private suspend fun ready(): AppRepository = model.repository.filterNotNull().first()
    private suspend fun idle() { model.ui.first { !it.busy } }

    @Test fun cancelledExportDoesNotMarkBackedUp() = runTest {
        val repository = ready()
        repository.execute(Action.SelectPhase(Phase.INTRO_ONE))
        val before = repository.state.value
        model.beginExport(false) { }
        idle()
        model.completeExport(null)
        assertEquals(before, repository.state.value)
        assertTrue(model.ui.value.message.orEmpty().contains("canceled"))
    }

    @Test fun successfulFileExportIsReadableAndMarksCapturedRevision() = runTest {
        val repository = ready()
        repository.execute(Action.SelectPhase(Phase.INTRO_TWO))
        val file = File(app.cacheDir, "export-test.json")
        model.beginExport(false) { }
        idle()
        model.completeExport(Uri.fromFile(file))
        idle()
        assertTrue(file.length() > 0)
        assertEquals(Phase.INTRO_TWO, repository.previewBackup(file.readText()).state.phase)
        assertEquals(repository.state.value.revision, repository.state.value.lastBackupRevision)
    }

    @Test fun unreadableAndCorruptFilesDoNotReplaceData() = runTest {
        val repository = ready()
        repository.execute(Action.SelectPhase(Phase.BASELINE))
        val before = repository.state.value
        model.readRestore(Uri.fromFile(File(app.cacheDir, "missing.json")))
        idle()
        assertEquals(before, repository.state.value)
        assertNotNull(model.ui.value.message)
        val bad = File(app.cacheDir, "bad.json").apply { writeText("{ broken") }
        model.readRestore(Uri.fromFile(bad))
        idle()
        assertEquals(before, repository.state.value)
        assertNull(model.ui.value.preview)
    }

    @Test fun invalidUtf8AndOversizedFilesAreRejectedWithoutMutation() = runTest {
        val repository = ready()
        repository.execute(Action.SelectPhase(Phase.INTRO_TWO))
        val before = repository.state.value
        val badEncoding = File(app.cacheDir, "invalid-utf8.json").apply {
            writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
        }
        model.readRestore(Uri.fromFile(badEncoding))
        idle()
        assertEquals(before, repository.state.value)
        assertNotNull(model.ui.value.message)
        val oversized = File(app.cacheDir, "oversized.json")
        java.io.RandomAccessFile(oversized, "rw").use { it.setLength(BackupViewModel.MAX_BACKUP_BYTES.toLong() + 1) }
        model.readRestore(Uri.fromFile(oversized))
        idle()
        assertEquals(before, repository.state.value)
        assertTrue(model.ui.value.message.orEmpty().contains("10 MiB"))
    }

    @Test fun failedWriteDoesNotMarkBackupOrAllowReplacement() = runTest {
        val repository = ready()
        repository.execute(Action.SelectPhase(Phase.INTRO_ONE))
        val before = repository.state.value
        model.beginExport(false) { }
        idle()
        model.completeExport(Uri.fromFile(app.cacheDir))
        idle()
        assertEquals(before, repository.state.value)
        assertNull(model.ui.value.safetyRevision)
        assertNotNull(model.ui.value.message)
    }

    @Test fun previewCancelAndSafetyBackupBeforeAtomicReplacement() = runTest {
        val repository = ready()
        repository.execute(Action.SelectPhase(Phase.INTRO_ONE))
        val old = File(app.cacheDir, "old.json").apply { writeText(repository.exportBackup().json) }
        repository.execute(Action.SelectPhase(Phase.BASELINE))
        val before = repository.state.value
        model.readRestore(Uri.fromFile(old))
        idle()
        assertEquals(Phase.INTRO_ONE, model.ui.value.preview?.state?.phase)
        assertEquals(before, repository.state.value)
        model.cancelRestore()
        assertEquals(before, repository.state.value)
        model.readRestore(Uri.fromFile(old))
        idle()
        model.confirmRestore()
        idle()
        assertEquals(before, repository.state.value)
        model.dismissMessage()
        val safety = File(app.cacheDir, "safety.json")
        model.beginExport(true) { }
        idle()
        model.completeExport(Uri.fromFile(safety))
        idle()
        assertEquals(Phase.BASELINE, repository.previewBackup(safety.readText()).state.phase)
        assertEquals(before.revision, model.ui.value.safetyRevision)
        model.confirmRestore()
        idle()
        assertEquals(Phase.INTRO_ONE, repository.state.value.phase)
        assertNull(model.ui.value.preview)
    }

    @Test fun sharedFileDoesNotClaimVerifiedBackup() = runTest {
        val repository = ready()
        repository.execute(Action.SelectPhase(Phase.FIVE_TWO))
        val before = repository.state.value
        var shared: File? = null
        model.prepareShare { shared = it }
        idle()
        assertNotNull(shared)
        assertEquals(Phase.FIVE_TWO, repository.previewBackup(requireNotNull(shared).readText()).state.phase)
        assertEquals(before, repository.state.value)
    }
}
