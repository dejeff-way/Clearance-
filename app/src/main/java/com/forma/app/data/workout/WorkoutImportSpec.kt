package com.forma.app.data.workout

/** Instructions to paste into ChatGPT so it produces importable workouts and programs. */
object WorkoutImportSpec {
    const val PROMPT = """When I ask for workouts or a training program, reply ONLY with JSON in the FORMA Workout Import v1 format:

{
  "schema": "forma.workout",
  "version": 1,
  "workouts": [
    {
      "id": "ski-legs-001",
      "name": "Ski-Ready Legs",
      "category": "Legs | Upper Body | Lower Body | Full Body | Push | Pull | Core | Cardio | Mobility | Conditioning",
      "difficulty": "Beginner | Intermediate | Advanced",
      "duration_minutes": 50,
      "goal": ["Strength", "Ski preparation"],
      "notes": "Optional coaching notes.",
      "tags": ["ski-prep", "lower-body"],
      "progression": { "type": "double_progression", "increment_lb": 5 },
      "exercises": [
        {
          "id": "leg-press",
          "name": "Leg Press",
          "sets": 3,
          "reps": "10-12",
          "rest_seconds": 90,
          "equipment": "Leg press machine",
          "instructions": "Lower under control; press without locking the knees.",
          "alternatives": ["Goblet Squat", "Hack Squat"],
          "muscle_groups": ["Quadriceps", "Glutes"]
        },
        { "id": "incline-walk", "name": "Incline Treadmill Walk", "sets": 1, "duration_minutes": 15, "rest_seconds": 0,
          "type": "cardio", "alternatives": ["Stationary Bike", "Elliptical"] }
      ]
    }
  ],
  "programs": [
    {
      "id": "ski-8-week",
      "name": "8-Week Ski Prep",
      "description": "Optional.",
      "weeks": 8,
      "schedule": [
        { "day": "Sunday", "workout": "upper-a" },
        { "day": "Monday", "workout": "ski-legs-001" },
        { "week": 5, "day": "Monday", "workout": "ski-legs-002" },
        { "day": "Friday", "rest": true, "label": "Shabbos" },
        { "day": "Saturday", "workout": "mobility-001", "optional": true }
      ]
    }
  ]
}

Rules:
- Every exercise needs sets, and either "reps" (e.g. "10-12") or a duration ("duration_minutes" / "duration_seconds"), plus rest_seconds.
- Give 1–3 "alternatives" for every exercise that train the same muscles or movement pattern.
- progression type: "double_progression" (add weight once every set reaches the top of the rep range), "linear" (add weight each successful session) or "none".
- Schedule entries without "week" apply to every week; an entry with "week" overrides that week only. Use "rest": true for rest days and "optional": true for optional sessions.
- Workouts referenced in a program must be in the same file (or already in FORMA). Use unique ids; reuse an id to update a workout later.
- Training days are Sunday–Thursday; Friday is rest for Shabbos; Saturday is optional (after Shabbos)."""
}
