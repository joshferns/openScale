/*
 * openScale
 * Copyright (C) 2026 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.health.openscale.core.database

import com.health.openscale.core.data.MeasurementTypeKey
import com.health.openscale.core.data.UnitType
import com.health.openscale.core.model.MeasurementValueWithType
import com.health.openscale.core.utils.ConverterUtils

/**
 * Derives [DatabaseProvider]'s pre-Phase-2 fixed measurement columns (weight in kg, fat/water/
 * muscle in percent) from a measurement's generic value set.
 *
 * `values_json` is the single source of truth since Phase 2, but sync clients built before it
 * existed hard-code these column names and require all four to be present on every row. The
 * provider keeps emitting them alongside `values_json` in its default projection so those older
 * clients keep working against newer openScale versions.
 */
object LegacyMeasurementColumns {
    data class Row(
        val weightKg: Float?,
        val fatPercent: Float,
        val waterPercent: Float,
        val musclePercent: Float
    )

    fun derive(values: List<MeasurementValueWithType>): Row {
        val valuesByKey = values.associateBy { it.type.key }
        val weightInKg = valuesByKey[MeasurementTypeKey.WEIGHT]?.let { weight ->
            weight.value.floatValue?.let { ConverterUtils.convertFloatValueUnit(it, weight.type.unit, UnitType.KG) }
        }

        fun percentOf(key: MeasurementTypeKey): Float? {
            val entry = valuesByKey[key] ?: return null
            val value = entry.value.floatValue ?: return null
            return when {
                entry.type.unit == UnitType.PERCENT -> value
                entry.type.unit.isWeightUnit() && weightInKg != null && weightInKg > 0 -> {
                    val valueInKg = ConverterUtils.convertFloatValueUnit(value, entry.type.unit, UnitType.KG)
                    (valueInKg / weightInKg) * 100f
                }
                else -> null
            }
        }

        return Row(
            weightKg = weightInKg,
            fatPercent = percentOf(MeasurementTypeKey.BODY_FAT) ?: 0.0f,
            waterPercent = percentOf(MeasurementTypeKey.WATER) ?: 0.0f,
            musclePercent = percentOf(MeasurementTypeKey.MUSCLE) ?: 0.0f
        )
    }
}
