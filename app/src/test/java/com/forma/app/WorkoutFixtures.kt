package com.forma.app

object WorkoutFixtures {
    /** The example from the workout import spec, verbatim. */
    const val USER_EXAMPLE = """
{
  "schema": "forma.workout",
  "version": 1,
  "workouts": [
    {
      "id": "ski-legs-001",
      "name": "Ski-Ready Legs",
      "category": "Legs",
      "difficulty": "Beginner",
      "duration_minutes": 50,
      "goal": ["Strength", "Ski preparation", "Endurance"],
      "exercises": [
        {
          "id": "leg-press", "name": "Leg Press", "sets": 3, "reps": "10-12", "rest_seconds": 90,
          "equipment": "Leg press machine",
          "instructions": "Lower the platform under control and press without locking your knees.",
          "alternatives": ["Goblet Squat", "Hack Squat"],
          "muscle_groups": ["Quadriceps", "Glutes"]
        },
        {
          "id": "seated-leg-curl", "name": "Seated Leg Curl", "sets": 3, "reps": "12", "rest_seconds": 60,
          "equipment": "Seated leg curl machine",
          "instructions": "Curl smoothly and control the return.",
          "alternatives": ["Lying Leg Curl"],
          "muscle_groups": ["Hamstrings"]
        }
      ],
      "tags": ["ski-prep", "lower-body", "strength"]
    }
  ]
}"""

    fun workout(id: String, name: String = "Workout $id", sets: Int = 3, extraExercise: String = "") = """
    {
      "id": "$id", "name": "$name", "category": "Full Body", "difficulty": "Intermediate", "duration_minutes": 45,
      "progression": { "type": "linear", "increment_lb": 5 },
      "exercises": [
        { "id": "squat", "name": "Barbell Back Squat", "sets": $sets, "reps": "5", "rest_seconds": 150,
          "alternatives": ["Goblet Squat", { "name": "Safety Bar Squat", "instructions": "Hands on the handles." }], "muscle_groups": ["Quadriceps"] },
        { "id": "bike", "name": "Stationary Bike", "sets": 1, "duration_minutes": 10, "rest_seconds": 0, "type": "cardio", "alternatives": ["Elliptical"] }
        $extraExercise
      ]
    }"""

    val EIGHT_WEEK = """
{
  "schema": "forma.workout", "version": 1,
  "workouts": [ ${workout("w-a", "Strength A")}, ${workout("w-b", "Strength B")}, ${workout("w-peak", "Peak Legs")} ],
  "programs": [
    {
      "id": "ski-8-week", "name": "8-Week Ski Prep", "weeks": 8,
      "schedule": [
        { "day": "Sunday", "workout": "w-a" },
        { "day": "Monday", "workout": "w-b" },
        { "week": 5, "day": "Monday", "workout": "w-peak" },
        { "day": "Wednesday", "workout": "w-a" },
        { "day": "Friday", "rest": true, "label": "Shabbos" },
        { "day": "Saturday", "workout": "w-b", "optional": true }
      ]
    }
  ]
}"""

    fun file(vararg workouts: String) = """{ "schema": "forma.workout", "version": 1, "workouts": [ ${workouts.joinToString(",")} ] }"""
}
