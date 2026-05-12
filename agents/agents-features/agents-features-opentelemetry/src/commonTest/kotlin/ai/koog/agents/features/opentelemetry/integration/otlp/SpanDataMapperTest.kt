package ai.koog.agents.features.opentelemetry.integration.otlp

import ai.koog.agents.features.opentelemetry.integration.otlp.OtlpSpanDataMapper.toOtlpExportRequest
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpanDataMapperTest {

    @Test
    fun testMapsAllSpanKindsToOtlpOrdinals() {
        val mapped = SpanKind.entries.associateWith { kind ->
            val span = FakeSpanData(spanKind = kind)
            val otlp = listOf(span)
                .toOtlpExportRequest()
                .resourceSpans.single().scopeSpans.single().spans.single()

            otlp.kind
        }

        // Asserting literals here is intentional, using `SpanKind.X.ordinal` would silently
        // match a buggy mapper that "just uses .ordinal", which produces wrong wire codes.
        assertEquals(1, mapped[SpanKind.INTERNAL])
        assertEquals(2, mapped[SpanKind.SERVER])
        assertEquals(3, mapped[SpanKind.CLIENT])
        assertEquals(4, mapped[SpanKind.PRODUCER])
        assertEquals(5, mapped[SpanKind.CONSUMER])
    }

    @Test
    fun testMapsAttributeValueTypes() {
        val span = FakeSpanData(
            attributes = mapOf(
                "s" to "hello",
                "b" to true,
                "l" to 7L,
                "i" to 7,
                "d" to 1.5,
                "f" to 1.5f,
                "list-string" to listOf("a", "b"),
            ),
        )

        val attrs = listOf(span).toOtlpExportRequest()
            .resourceSpans.single().scopeSpans.single().spans.single().attributes!!
            .associate { it.key to it.value }

        assertEquals("hello", attrs["s"]?.stringValue)
        assertEquals(true, attrs["b"]?.boolValue)

        // int64 values are JSON strings per the OTLP/JSON spec (avoids 64-bit precision loss in JS clients).
        assertEquals("7", attrs["l"]?.intValue)
        assertEquals("7", attrs["i"]?.intValue)
        assertEquals(1.5, attrs["d"]?.doubleValue)
        assertEquals(1.5, attrs["f"]?.doubleValue)
        val arr = attrs["list-string"]?.arrayValue?.values?.map { it.stringValue }
        assertEquals(listOf("a", "b"), arr)
    }

    @Test
    fun testParentSpanIdGatedByValidity() {
        val absent = listOf(FakeSpanData(parent = FakeSpanContext(isValid = false)))
            .toOtlpExportRequest().resourceSpans.single().scopeSpans.single().spans.single()
        assertNull(absent.parentSpanId, "Invalid parent must be omitted, not encoded as zeros")

        val present = listOf(FakeSpanData(parent = FakeSpanContext(spanId = "test-span-id", isValid = true)))
            .toOtlpExportRequest().resourceSpans.single().scopeSpans.single().spans.single()
        assertEquals("test-span-id", present.parentSpanId)
    }

    @Test
    fun testStatusCodeAndDescription() {
        val ok = listOf(FakeSpanData(status = StatusData.Ok))
            .toOtlpExportRequest().resourceSpans.single().scopeSpans.single().spans.single()
        assertEquals(1, ok.status?.code)
        assertNull(ok.status?.message)

        val error = listOf(FakeSpanData(status = StatusData.Error("boom")))
            .toOtlpExportRequest().resourceSpans.single().scopeSpans.single().spans.single()
        assertEquals(2, error.status?.code)
        assertEquals("boom", error.status?.message)

        val unset = listOf(FakeSpanData(status = StatusData.Unset))
            .toOtlpExportRequest().resourceSpans.single().scopeSpans.single().spans.single()
        assertEquals(0, unset.status?.code)
    }

    @Test
    fun testGroupsSpansByResourceAndScope() {
        val resourceA = FakeResource(attributes = mapOf("service.name" to "a"))
        val resourceB = FakeResource(attributes = mapOf("service.name" to "b"))
        val scope1 = FakeInstrumentationScopeInfo(name = "scope-1")
        val scope2 = FakeInstrumentationScopeInfo(name = "scope-2")

        val spans = listOf(
            FakeSpanData(name = "a-1", resource = resourceA, instrumentationScopeInfo = scope1),
            FakeSpanData(name = "a-2", resource = resourceA, instrumentationScopeInfo = scope2),
            FakeSpanData(name = "b-1", resource = resourceB, instrumentationScopeInfo = scope1),
        )

        val request = spans.toOtlpExportRequest()
        assertEquals(2, request.resourceSpans.size, "Two distinct resources")

        val byService = request.resourceSpans.associateBy { rs ->
            rs.resource.attributes.first { it.key == "service.name" }.value.stringValue
        }
        // Resource A has both scope-1 and scope-2 → 2 scopeSpans groups.
        assertEquals(2, byService["a"]?.scopeSpans?.size)

        // Resource B has only scope-1 → 1 scopeSpans group.
        assertEquals(1, byService["b"]?.scopeSpans?.size)
    }

    @Test
    fun testMapsEventsAndLinks() {
        val linkContext = FakeSpanContext(spanId = "test-span-id", isValid = true)
        val span = FakeSpanData(
            events = listOf(
                FakeSpanEventData(name = "e1", timestamp = 1_700_000_000_500_000_000L, attributes = mapOf("k" to "v")),
            ),
            links = listOf(
                FakeSpanLinkData(spanContext = linkContext, attributes = mapOf("rel" to "follows")),
            ),
        )

        val otlp = listOf(span).toOtlpExportRequest()
            .resourceSpans.single().scopeSpans.single().spans.single()

        val event = otlp.events!!.single()
        assertEquals("e1", event.name)
        assertEquals("1700000000500000000", event.timeUnixNano)
        assertEquals("v", event.attributes!!.single { it.key == "k" }.value.stringValue)

        val link = otlp.links!!.single()
        assertEquals(linkContext.traceId, link.traceId)
        assertEquals(linkContext.spanId, link.spanId)
        assertEquals("follows", link.attributes!!.single { it.key == "rel" }.value.stringValue)
    }
}
