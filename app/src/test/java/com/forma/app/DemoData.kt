package com.forma.app

import com.forma.app.data.db.DailyEnergyEntity
import com.forma.app.data.seed.Category
import com.forma.app.data.seed.WorkoutProgram
import com.forma.app.di.AppContainer
import com.forma.engine.CarryoverKind
import com.forma.engine.Portion
import java.time.LocalDate

/**
 * TEST-ONLY sample history used to render realistic screenshots. The shipping app never inserts sample data.
 */
object DemoData {
    suspend fun load(c: AppContainer, today: LocalDate, withHealth: Boolean = true) {
        val zone = c.time.zone()
        val weights = listOf(260.2, 259.6, 260.0, 259.1, 258.8, 259.3, 258.4, 258.0, 258.6, 257.7, 257.9, 257.2, 257.5, 256.8,
            257.0, 256.4, 256.9, 256.1, 255.8, 256.2, 255.5)
        weights.forEachIndexed { i, w -> c.body.logWeight(w, today.minusDays((weights.size - 1 - i).toLong()).toEpochDay()) }
        c.body.addMeasurement(46.0, today.minusDays(20).toEpochDay(), null)
        c.body.addMeasurement(45.5, today.minusDays(13).toEpochDay(), null)
        c.body.addMeasurement(45.0, today.minusDays(6).toEpochDay(), "after breakfast")
        for (d in 1..13) {
            val day = today.minusDays(d.toLong())
            val noon = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            c.food.logRecipe("protein_overnight_oats", Category.BREAKFAST.name, Portion.STANDARD, 1.0, day.toEpochDay(), noon - 4 * 3_600_000)
            c.food.logRecipe(if (d % 2 == 0) "chicken_shawarma_bowl" else "salmon_poke_bowl", Category.LUNCH.name, Portion.STANDARD, 1.0, day.toEpochDay(), noon)
            c.food.logRecipe(if (d % 3 == 0) "garlic_london_broil" else "garlic_chicken_potatoes", Category.SUPPER.name, if (d % 4 == 0) Portion.LARGE else Portion.STANDARD, 1.0, day.toEpochDay(), noon + 6 * 3_600_000)
            c.food.logRecipe("chocolate_tnuva_creami", Category.SNACKS.name, Portion.STANDARD, 1.0, day.toEpochDay(), noon + 9 * 3_600_000)
            if (d == 3) c.food.logManual("Kiddush", Category.SNACKS.name, 750.0, 15.0, null, null, day.toEpochDay(), noon + 2 * 3_600_000)
            if (withHealth) c.db.energy().upsert(DailyEnergyEntity(day.toEpochDay(), hcTotalKcal = 2850.0 + (d * 37 % 5) * 90, steps = 7000L + d * 613 % 5000, sleepMinutes = 410.0 + d * 7 % 60))
        }
        val t = today.atTime(8, 10).atZone(zone).toInstant().toEpochMilli()
        c.food.logRecipe("protein_egg_scramble", Category.BREAKFAST.name, Portion.STANDARD, 1.0, today.toEpochDay(), t)
        c.food.logRecipe("chicken_fajita_bowl", Category.LUNCH.name, Portion.STANDARD, 1.0, today.toEpochDay(), t + 4 * 3_600_000)
        if (withHealth) c.db.energy().upsert(DailyEnergyEntity(today.toEpochDay(), hcTotalKcal = 1720.0, steps = 6420, sleepMinutes = 432.0))
        c.carryover.add(300, CarryoverKind.OVERAGE.name, today.minusDays(3).toEpochDay(), "Kiddush")
        // Workouts this week and last
        for (d in 1..9) {
            val day = today.minusDays(d.toLong())
            val plan = WorkoutProgram.forDay(day.dayOfWeek)
            if (plan.rest || plan.optional) continue
            val sid = c.workouts.ensureSession(day, plan.key)
            plan.slots.forEachIndexed { i, s ->
                if (c.time.today() == today) c.workouts.logSet(sid, s.key, 0, if (s.timed) null else 60.0 + i * 10, if (s.timed) null else 12, if (s.timed) 15.0 else null)
                c.workouts.setCompleted(sid, s.key, true)
            }
        }
        if (withHealth) c.db.syncMeta().upsert(com.forma.app.data.db.SyncMetaEntity(lastSuccessAt = c.time.nowMillis() - 600_000,
            lastAttemptAt = c.time.nowMillis() - 600_000, grantedPermissions = "TOTAL_CALORIES,STEPS,EXERCISE,WEIGHT,SLEEP"))
    }
}
