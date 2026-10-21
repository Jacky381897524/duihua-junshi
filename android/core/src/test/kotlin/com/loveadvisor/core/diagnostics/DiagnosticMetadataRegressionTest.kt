package com.loveadvisor.core.diagnostics
import java.nio.file.Files
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class DiagnosticMetadataRegressionTest {
    @Test fun unknownScreenshotCodeIsNotRewrittenToSuccessfulZero() {
        val file=Files.createTempDirectory("diagnostic-code").resolve("log.json").toFile()
        val journal=DiagnosticJournal({file},Executor { it.run() },"V1.4.11",41,100)
        journal.event(LogStep.SCREENSHOT_RESULT,LogState.FAIL,LogReason.SCREENSHOT_FAILED,metrics=mapOf(LogMetric.SCREENSHOT_CODE to -1))
        assertEquals(-1L,journal.snapshot().events.last().metrics[LogMetric.SCREENSHOT_CODE])
    }
}
