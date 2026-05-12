package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.platform.PlatformInfo
import ai.koog.agents.features.opentelemetry.platform.loadProductProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.export.TelemetryCloseable
import io.opentelemetry.kotlin.factory.ContextFactory
import io.opentelemetry.kotlin.init.TraceExportConfigDsl
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.export.batchSpanProcessor
import io.opentelemetry.kotlin.tracing.export.compositeSpanProcessor
import io.opentelemetry.kotlin.tracing.export.simpleSpanProcessor
import io.opentelemetry.kotlin.tracing.export.stdoutSpanExporter
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger { }

internal class OpenTelemetryConfigImpl : OpenTelemetryConfigAPI {

    private val productProperties = loadProductProperties()

    internal val customSpanProcessorFactories: MutableList<TraceExportConfigDsl.() -> SpanProcessor> = mutableListOf()

    internal val customResourceAttributes: MutableMap<String, Any> = mutableMapOf()

    internal val platform: OpenTelemetryPlatform = OpenTelemetryPlatform()

    private var _sdk: OpenTelemetry? = null

    @OptIn(ExperimentalUuidApi::class)
    private val _instanceId: String = Uuid.random().toString()

    private var _serviceName: String = productProperties["name"] ?: "ai.koog"

    private var _serviceVersion: String = productProperties["version"] ?: "0.0.0"

    private var _serviceNamespace: String? = null

    internal var instrumentationScopeName: String = _serviceName
        private set

    internal var instrumentationScopeVersion: String = _serviceVersion
        private set

    private var _verbose: Boolean = false

    private var _spanAdapter: SpanAdapter? = null

    private var _shutdownOnAgentClose: Boolean = false

    override val isVerbose: Boolean
        get() = _verbose

    override val tracer: Tracer
        get() = sdk.tracerProvider.getTracer(instrumentationScopeName, instrumentationScopeVersion)

    internal val contextFactory: ContextFactory
        get() = sdk.context

    internal val sdk: OpenTelemetry
        get() = _sdk ?: initializeOpenTelemetry().also { sdk ->
            _sdk = sdk
            instrumentationScopeName = _serviceName
            instrumentationScopeVersion = _serviceVersion
        }

    override val serviceName: String
        get() = _serviceName

    override val serviceVersion: String
        get() = _serviceVersion

    override val serviceNamespace: String?
        get() = _serviceNamespace

    override val isShutdownOnAgentClose: Boolean
        get() = _shutdownOnAgentClose

    internal val spanAdapter: SpanAdapter?
        get() = _spanAdapter

    override fun setServiceInfo(serviceName: String, serviceVersion: String, serviceNamespace: String?) {
        _serviceName = serviceName
        _serviceVersion = serviceVersion
        _serviceNamespace = serviceNamespace
    }

    override fun addSpanExporter(exporter: SpanExporter) {
        addSpanProcessor { batchSpanProcessor(exporter) }
    }

    override fun addSpanProcessor(factory: TraceExportConfigDsl.() -> SpanProcessor) {
        customSpanProcessorFactories.add(factory)
    }

    override fun addResourceAttributes(attributes: Map<String, Any>) {
        customResourceAttributes.putAll(attributes)
    }

    override fun setVerbose(verbose: Boolean) {
        _verbose = verbose
    }

    override fun setSdk(openTelemetry: OpenTelemetry) {
        _sdk = openTelemetry
    }

    override fun setShutdownOnAgentClose(shutdownOnAgentClose: Boolean) {
        _shutdownOnAgentClose = shutdownOnAgentClose
    }

    override fun addSpanAdapter(adapter: SpanAdapter) {
        _spanAdapter = adapter
    }

    internal suspend fun closeSdks() {
        platform.shutdown()

        val telemetryCloseable = _sdk as? TelemetryCloseable
        if (telemetryCloseable == null) {
            logger.warn { "OpenTelemetry SDK instance is not a TelemetryCloseable. Skip shutdown." }
            return
        }

        logger.debug { "Cascade shutdown for the OpenTelemetry SDK." }
        runCatching {
            telemetryCloseable.shutdown()
        }.onFailure { t ->
            logger.warn(t) { "Failed to shutdown OpenTelemetry SDK." }
        }
    }

    internal fun buildResourceMap(): Map<String, Any> = buildMap {
        put("service.name", _serviceName)
        put("service.version", _serviceVersion)
        put("service.instance.id", _instanceId)
        _serviceNamespace?.let { put("service.namespace", it) }

        PlatformInfo.osName?.let { osName -> put("os.type", osName) }
        PlatformInfo.osVersion?.let { osVersion -> put("os.version", osVersion) }
        PlatformInfo.osArch?.let { osArch -> put("os.arch", osArch) }

        putAll(customResourceAttributes)
    }

    private fun initializeOpenTelemetry(): OpenTelemetry {
        val resourceMap = buildResourceMap()
        val preConfiguredFactories = customSpanProcessorFactories.toList()

        val sdk = createOpenTelemetry {
            tracerProvider {
                resource(resourceMap)
                export {
                    val processors = if (preConfiguredFactories.isEmpty()) {
                        logger.debug { "No custom span processors configured. Using simple stdout processor by default." }
                        listOf(simpleSpanProcessor(stdoutSpanExporter()))
                    } else {
                        preConfiguredFactories.map { factory ->
                            factory().also { processor ->
                                logger.debug { "Adding span processor: ${processor::class.simpleName}" }
                            }
                        }
                    }

                    processors.singleOrNull()
                        ?: compositeSpanProcessor(*processors.toTypedArray())
                }
            }
        }

        return sdk
    }
}
