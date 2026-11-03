package com.sanjay.pocketmind.core.tools

import java.math.BigDecimal
import java.math.MathContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Converts between units with exact factors (the international definitions: 1 inch = 0.0254 m,
 * 1 lb = 0.45359237 kg, 1 US gallon = 3.785411784 L …), so no conversion is "remembered" wrongly.
 */
class UnitConverterTool : LocalTool {
    override val name = NAME
    override val description =
        "Converts a value between units. Use it for every unit conversion. Units: length (mm, cm, m, km, in, ft, " +
            "yd, mi, nmi), mass (mg, g, kg, t, oz, lb), volume (ml, l, tsp, tbsp, cup, fl_oz, gal), temperature " +
            "(c, f, k), speed (m/s, km/h, mph, knot), area (cm2, m2, km2, ha, acre, ft2), data (b, kb, mb, gb, tb)."
    override val parameters = listOf(
        ToolParameter("value", ParameterType.NUMBER, "The number to convert"),
        ToolParameter("from", ParameterType.STRING, "The unit of the value, e.g. km"),
        ToolParameter("to", ParameterType.STRING, "The unit to convert to, e.g. mi"),
    )

    override fun run(arguments: JsonObject): JsonObject {
        val value = arguments.requireNumber("value")
        val from = unit(arguments.requireText("from"))
        val to = unit(arguments.requireText("to"))
        if (from.dimension != to.dimension) {
            val problem = "can't convert ${from.dimension} (${from.symbol}) to ${to.dimension} (${to.symbol})"
            throw ToolArgumentException(problem)
        }
        val result = if (from.dimension == TEMPERATURE) {
            Temperature.convert(value, from.symbol, to.symbol)
        } else {
            value.multiply(from.factor).divide(to.factor, MathContext.DECIMAL128)
        }
        return buildJsonObject {
            put("value", Expression.format(value))
            put("from", from.symbol)
            put("to", to.symbol)
            put("result", Expression.format(result))
        }
    }

    /** A unit: its dimension and how many base units (m, kg, l, km/h, m², byte) one of it is. */
    private data class MeasureUnit(val symbol: String, val dimension: String, val factor: BigDecimal)

    private fun unit(raw: String): MeasureUnit {
        val key = raw.trim().lowercase().replace(" ", "").replace("²", "2").replace("°", "")
        return UNITS[ALIASES[key] ?: key] ?: throw ToolArgumentException("unknown unit '$raw'")
    }

    private object Temperature {
        private val NINE_FIFTHS = BigDecimal("1.8")
        private val FREEZING_F = BigDecimal(32)
        private val ZERO_C_IN_K = BigDecimal("273.15")

        fun convert(value: BigDecimal, from: String, to: String): BigDecimal = fromCelsius(toCelsius(value, from), to)

        private fun toCelsius(value: BigDecimal, unit: String): BigDecimal = when (unit) {
            "f" -> value.subtract(FREEZING_F).divide(NINE_FIFTHS, MathContext.DECIMAL128)
            "k" -> value.subtract(ZERO_C_IN_K)
            else -> value
        }

        private fun fromCelsius(celsius: BigDecimal, unit: String): BigDecimal = when (unit) {
            "f" -> celsius.multiply(NINE_FIFTHS).add(FREEZING_F)
            "k" -> celsius.add(ZERO_C_IN_K)
            else -> celsius
        }
    }

    companion object {
        const val NAME = "unit_converter"
        private const val TEMPERATURE = "temperature"

        private val UNITS: Map<String, MeasureUnit> = listOf(
            "length" to listOf(
                "mm" to "0.001", "cm" to "0.01", "m" to "1", "km" to "1000", "in" to "0.0254",
                "ft" to "0.3048", "yd" to "0.9144", "mi" to "1609.344", "nmi" to "1852",
            ),
            "mass" to listOf(
                "mg" to "0.000001",
                "g" to "0.001",
                "kg" to "1",
                "t" to "1000",
                "oz" to "0.028349523125",
                "lb" to "0.45359237",
            ),
            "volume" to listOf(
                "ml" to "0.001",
                "l" to "1",
                "tsp" to "0.00492892159375",
                "tbsp" to "0.01478676478125",
                "cup" to "0.2365882365",
                "fl_oz" to "0.0295735295625",
                "gal" to "3.785411784",
            ),
            TEMPERATURE to listOf("c" to "1", "f" to "1", "k" to "1"),
            "speed" to listOf("km/h" to "1", "m/s" to "3.6", "mph" to "1.609344", "knot" to "1.852"),
            "area" to listOf(
                "cm2" to "0.0001",
                "m2" to "1",
                "km2" to "1000000",
                "ha" to "10000",
                "acre" to "4046.8564224",
                "ft2" to "0.09290304",
            ),
            "data" to listOf(
                "b" to "1",
                "kb" to "1000",
                "mb" to "1000000",
                "gb" to "1000000000",
                "tb" to "1000000000000",
            ),
        ).flatMap { (dimension, units) ->
            units.map { (symbol, factor) -> symbol to MeasureUnit(symbol, dimension, BigDecimal(factor)) }
        }.toMap()

        /** Other ways to write the same unit. */
        private val ALIASES = mapOf(
            "millimeter" to "mm", "millimetre" to "mm", "centimeter" to "cm", "centimetre" to "cm",
            "meter" to "m", "metre" to "m", "meters" to "m", "metres" to "m",
            "kilometer" to "km", "kilometre" to "km", "kilometers" to "km", "kilometres" to "km",
            "inch" to "in", "inches" to "in", "foot" to "ft", "feet" to "ft", "yard" to "yd", "yards" to "yd",
            "mile" to "mi", "miles" to "mi", "nauticalmile" to "nmi",
            "gram" to "g", "grams" to "g", "kilogram" to "kg", "kilograms" to "kg", "kgs" to "kg",
            "milligram" to "mg", "tonne" to "t", "tonnes" to "t", "ounce" to "oz", "ounces" to "oz",
            "pound" to "lb", "pounds" to "lb", "lbs" to "lb",
            "liter" to "l", "litre" to "l", "liters" to "l", "litres" to "l",
            "milliliter" to "ml", "millilitre" to "ml",
            "teaspoon" to "tsp", "tablespoon" to "tbsp", "cups" to "cup", "floz" to "fl_oz", "gallon" to "gal",
            "gallons" to "gal",
            "celsius" to "c", "fahrenheit" to "f", "kelvin" to "k",
            "kmh" to "km/h", "kph" to "km/h", "mps" to "m/s", "knots" to "knot", "kn" to "knot",
            "sqm" to "m2", "sqkm" to "km2", "sqft" to "ft2", "hectare" to "ha", "hectares" to "ha", "acres" to "acre",
            "byte" to "b", "bytes" to "b", "kilobyte" to "kb", "megabyte" to "mb",
            "gigabyte" to "gb", "terabyte" to "tb",
        )
    }
}
