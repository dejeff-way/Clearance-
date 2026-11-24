package com.forma.app.health

import androidx.health.connect.client.records.ExerciseSessionRecord as E

/** Readable names and rough MET values for Health Connect exercise types (used only in estimate mode). */
object ExerciseTypes {
    fun name(type: Int): String = when (type) {
        E.EXERCISE_TYPE_WALKING -> "Walk"
        E.EXERCISE_TYPE_RUNNING, E.EXERCISE_TYPE_RUNNING_TREADMILL -> "Run"
        E.EXERCISE_TYPE_BIKING, E.EXERCISE_TYPE_BIKING_STATIONARY -> "Cycling"
        E.EXERCISE_TYPE_STRENGTH_TRAINING, E.EXERCISE_TYPE_WEIGHTLIFTING -> "Strength training"
        E.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
        E.EXERCISE_TYPE_ROWING_MACHINE -> "Rowing"
        E.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE, E.EXERCISE_TYPE_STAIR_CLIMBING -> "Stair climbing"
        E.EXERCISE_TYPE_SKIING -> "Skiing"
        E.EXERCISE_TYPE_SWIMMING_POOL, E.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swimming"
        E.EXERCISE_TYPE_YOGA -> "Yoga"
        E.EXERCISE_TYPE_STRETCHING -> "Stretching"
        E.EXERCISE_TYPE_HIKING -> "Hike"
        E.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        else -> "Workout"
    }

    fun met(type: Int): Double = when (type) {
        E.EXERCISE_TYPE_WALKING -> 3.8
        E.EXERCISE_TYPE_RUNNING, E.EXERCISE_TYPE_RUNNING_TREADMILL -> 8.5
        E.EXERCISE_TYPE_BIKING, E.EXERCISE_TYPE_BIKING_STATIONARY -> 6.5
        E.EXERCISE_TYPE_STRENGTH_TRAINING, E.EXERCISE_TYPE_WEIGHTLIFTING -> 4.5
        E.EXERCISE_TYPE_ELLIPTICAL -> 5.5
        E.EXERCISE_TYPE_ROWING_MACHINE -> 6.0
        E.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE, E.EXERCISE_TYPE_STAIR_CLIMBING -> 7.5
        E.EXERCISE_TYPE_SKIING -> 6.5
        E.EXERCISE_TYPE_SWIMMING_POOL, E.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> 7.0
        E.EXERCISE_TYPE_YOGA, E.EXERCISE_TYPE_STRETCHING -> 2.5
        E.EXERCISE_TYPE_HIKING -> 6.0
        E.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> 8.0
        else -> 4.5
    }
}
