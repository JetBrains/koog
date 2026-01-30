package ai.koog.agents.features.opentelemetry.metrics

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.DoubleCounterBuilder
import io.opentelemetry.api.metrics.DoubleGaugeBuilder
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.DoubleHistogramBuilder
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongCounterBuilder
import io.opentelemetry.api.metrics.LongHistogramBuilder
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongCounter
import io.opentelemetry.api.metrics.ObservableLongMeasurement
import io.opentelemetry.context.Context
import java.util.function.Consumer

internal data class Metric(val name: String, val description: String?, val unit: String?)

internal data class MetricRecord<T>(val metric: Metric, val value: T, val attributes: Attributes?)

internal class TestMeter : Meter {
    val buildCounter: MutableList<Metric> = mutableListOf()
    val buildHistogram: MutableList<Metric> = mutableListOf()

    val counterValues: MutableList<MetricRecord<Long>> = mutableListOf()
    val histogramValues: MutableList<MetricRecord<Double>> = mutableListOf()

    override fun counterBuilder(name: String): LongCounterBuilder? {
        return object : LongCounterBuilder {
            var description: String? = null
            var unit: String? = null

            override fun setDescription(description: String?): LongCounterBuilder? {
                this.description = description

                return this
            }

            override fun setUnit(unit: String?): LongCounterBuilder? {
                this.unit = unit

                return this
            }

            override fun ofDoubles(): DoubleCounterBuilder? {
                TODO("Not yet implemented")
            }

            override fun build(): LongCounter? {
                val metric = Metric(name, description, unit)
                buildCounter.add(metric)

                return object : LongCounter {
                    override fun add(value: Long) {
                        counterValues.add(MetricRecord(metric, value, null))
                    }

                    override fun add(value: Long, attributes: Attributes?) {
                        counterValues.add(MetricRecord(metric, value, attributes))
                    }

                    override fun add(
                        value: Long,
                        attributes: Attributes?,
                        context: Context?
                    ) {
                        counterValues.add(MetricRecord(metric, value, attributes))
                    }
                }
            }

            override fun buildWithCallback(callback: Consumer<ObservableLongMeasurement?>?): ObservableLongCounter? {
                TODO("Not yet implemented")
            }
        }
    }

    override fun upDownCounterBuilder(name: String): LongUpDownCounterBuilder? {
        TODO("Not yet implemented")
    }

    override fun histogramBuilder(name: String): DoubleHistogramBuilder? {
        return object : DoubleHistogramBuilder {
            var description: String? = null
            var unit: String? = null

            override fun setDescription(description: String?): DoubleHistogramBuilder? {
                this.description = description

                return this
            }

            override fun setUnit(unit: String?): DoubleHistogramBuilder? {
                this.unit = unit

                return this
            }

            override fun ofLongs(): LongHistogramBuilder? {
                TODO("Not yet implemented")
            }

            override fun build(): DoubleHistogram {
                val metric = Metric(name, description, unit)
                buildHistogram.add(metric)

                return object : DoubleHistogram {
                    val currentMetric = metric

                    override fun record(value: Double) {
                        histogramValues.add(MetricRecord(currentMetric, value, null))
                    }

                    override fun record(value: Double, attributes: Attributes?) {
                        histogramValues.add(MetricRecord(currentMetric, value, attributes))
                    }

                    override fun record(
                        value: Double,
                        attributes: Attributes?,
                        context: Context?
                    ) {
                        histogramValues.add(MetricRecord(currentMetric, value, attributes))
                    }
                }
            }
        }
    }

    override fun gaugeBuilder(name: String?): DoubleGaugeBuilder? {
        TODO("Not yet implemented")
    }
}

internal fun TestMeter.getRecordsByCounterName(name: String): List<MetricRecord<Long>> {
    return this.counterValues.filter { it.metric.name == name }
}

internal fun TestMeter.getRecordsByHistogramName(name: String): List<MetricRecord<Double>> {
    return this.histogramValues.filter { it.metric.name == name }
}
