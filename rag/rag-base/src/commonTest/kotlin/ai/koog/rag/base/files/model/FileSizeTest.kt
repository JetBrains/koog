package ai.koog.rag.base.files.model

import kotlin.test.Test
import kotlin.test.assertEquals

class FileSizeTest {

    @Test
    fun `bytes display returns 0 bytes for zero value`() {
        assertEquals("0 bytes", FileSize.Bytes(0).display())
    }

    @Test
    fun `bytes display shows less than 0_1 KiB for small values`() {
        assertEquals("<0.1 KiB", FileSize.Bytes(50).display())
        assertEquals("<0.1 KiB", FileSize.Bytes(100).display())
        assertEquals("<0.1 KiB", FileSize.Bytes(102).display())
    }

    @Test
    fun `bytes display shows KiB with proper formatting for values at 0_1 KiB threshold`() {
        assertEquals("0.1 KiB", FileSize.Bytes(103).display())
        assertEquals("0.5 KiB", FileSize.Bytes(512).display())
    }

    @Test
    fun `bytes display shows KiB with proper formatting`() {
        assertEquals("1.0 KiB", FileSize.Bytes(1024).display())
        assertEquals("1.5 KiB", FileSize.Bytes(1500).display())
        assertEquals("2.0 KiB", FileSize.Bytes(2048).display())
        assertEquals("10.5 KiB", FileSize.Bytes(10752).display())
    }

    @Test
    fun `bytes display shows MiB with proper formatting for values at MiB threshold`() {
        assertEquals("1.0 MiB", FileSize.Bytes(FileSize.MIB).display())
        assertEquals("1.0 MiB", FileSize.Bytes(1024 * 1024).display())
    }

    @Test
    fun `bytes display shows MiB with proper formatting`() {
        assertEquals("1.5 MiB", FileSize.Bytes(1024 * 1024 + 512 * 1024).display())
        assertEquals("2.0 MiB", FileSize.Bytes(2 * 1024 * 1024).display())
    }

    @Test
    fun `bytes display handles edge case just below MiB threshold`() {
        assertEquals("10.3 MiB", FileSize.Bytes(103 * 1024 * 1024 / 10).display())
    }

    @Test
    fun `lines display shows correct count format`() {
        assertEquals("0 lines", FileSize.Lines(0).display())
        assertEquals("1 line", FileSize.Lines(1).display())
        assertEquals("42 lines", FileSize.Lines(42).display())
        assertEquals("1000 lines", FileSize.Lines(1000).display())
    }

    @Test
    fun `bytes display handles very large values`() {
        assertEquals("100.0 MiB", FileSize.Bytes(100 * FileSize.MIB).display())
        assertEquals("1000.0 MiB", FileSize.Bytes(1000 * FileSize.MIB).display())
    }
}
