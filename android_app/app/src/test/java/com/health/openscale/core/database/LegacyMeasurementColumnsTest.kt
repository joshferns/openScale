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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.health.openscale.core.database

import com.google.common.truth.Truth.assertThat
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.data.MeasurementTypeKey
import com.health.openscale.core.data.MeasurementValue
import com.health.openscale.core.data.UnitType
import com.health.openscale.core.model.MeasurementValueWithType
import org.junit.Test

/**
 * Unit tests for [LegacyMeasurementColumns], which reconstructs [DatabaseProvider]'s pre-Phase-2
 * fixed weight/fat/water/muscle columns from the generic value set. These columns are what sync
 * clients built before `values_json` existed hard-code and require to be non-null on every row
 * (see [DatabaseProvider]'s default measurement projection) - so their derivation must keep
 * matching the pre-Phase-2 behaviour exactly.
 */
class LegacyMeasurementColumnsTest {
    private companion object {
        const val EPS = 1e-3f

        val weightKg = MeasurementType(id = 1, key = MeasurementTypeKey.WEIGHT, unit = UnitType.KG)
        val weightLb = MeasurementType(id = 1, key = MeasurementTypeKey.WEIGHT, unit = UnitType.LB)
        val fatPercent = MeasurementType(id = 2, key = MeasurementTypeKey.BODY_FAT, unit = UnitType.PERCENT)
        val waterKg = MeasurementType(id = 3, key = MeasurementTypeKey.WATER, unit = UnitType.KG)
        val muscleKg = MeasurementType(id = 4, key = MeasurementTypeKey.MUSCLE, unit = UnitType.KG)
    }

    private fun withType(type: MeasurementType, value: Float) =
        MeasurementValueWithType(
            value = MeasurementValue(measurementId = 1, typeId = type.id, floatValue = value),
            type = type
        )

    @Test
    fun derive_convertsPercentTypesAsIs() {
        val row = LegacyMeasurementColumns.derive(
            listOf(withType(weightKg, 80f), withType(fatPercent, 18.4f))
        )

        assertThat(row.weightKg).isWithin(EPS).of(80f)
        assertThat(row.fatPercent).isWithin(EPS).of(18.4f)
    }

    @Test
    fun derive_convertsAbsoluteWeightUnitTypesToPercentOfBodyWeight() {
        // 16kg of water on an 80kg measurement is 20%.
        val row = LegacyMeasurementColumns.derive(
            listOf(withType(weightKg, 80f), withType(waterKg, 16f))
        )

        assertThat(row.waterPercent).isWithin(EPS).of(20f)
    }

    @Test
    fun derive_convertsWeightToKgRegardlessOfStoredUnit() {
        val row = LegacyMeasurementColumns.derive(listOf(withType(weightLb, 220.462f)))

        assertThat(row.weightKg).isWithin(EPS).of(100f)
    }

    @Test
    fun derive_defaultsMissingPercentTypesToZeroRatherThanNull() {
        // Old sync clients require fat/water/muscle to be non-null on every row.
        val row = LegacyMeasurementColumns.derive(listOf(withType(weightKg, 80f)))

        assertThat(row.fatPercent).isEqualTo(0.0f)
        assertThat(row.waterPercent).isEqualTo(0.0f)
        assertThat(row.musclePercent).isEqualTo(0.0f)
    }

    @Test
    fun derive_leavesWeightNullWhenNoWeightValueIsPresent() {
        val row = LegacyMeasurementColumns.derive(listOf(withType(fatPercent, 18.4f)))

        assertThat(row.weightKg).isNull()
    }

    @Test
    fun derive_cannotConvertAnAbsoluteCompositionValueWithoutAWeight() {
        // No weight to derive a percentage from -> falls back to the 0.0 default, same as absent.
        val row = LegacyMeasurementColumns.derive(listOf(withType(muscleKg, 30f)))

        assertThat(row.musclePercent).isEqualTo(0.0f)
    }
}
