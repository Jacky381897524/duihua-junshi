package com.loveadvisor.android.storage
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
class EncryptedStoreTest {
    @Test fun keystoreRoundTripLeavesNoPlaintextOnDisk() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=AndroidDocumentStore(context,"verification")
        store.write("probe","FAKE_DEVICE_KEY 私密资料")
        assertEquals("FAKE_DEVICE_KEY 私密资料",store.read("probe"))
        val bytes=context.filesDir.resolve("verification/probe.enc").readBytes().toString(Charsets.UTF_8)
        assertFalse(bytes.contains("FAKE_DEVICE_KEY")); assertFalse(bytes.contains("私密资料"))
        val first=context.filesDir.resolve("verification/probe.enc").readBytes()
        store.write("probe","FAKE_DEVICE_KEY 私密资料")
        val second=context.filesDir.resolve("verification/probe.enc").readBytes()
        assertFalse(first.copyOfRange(1,13).contentEquals(second.copyOfRange(1,13)))
        assertEquals("FAKE_DEVICE_KEY 私密资料",store.read("probe"))
        context.filesDir.resolve("verification/probe.enc").delete()
    }
}
