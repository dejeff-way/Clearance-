# FORMA Workout Import v1 (`forma.workout`)

Import in the app: **Workout → Import** (or Workout → Library → Import). You can import a `.json` file or paste JSON.
**Copy format instructions** copies the prompt below for ChatGPT. Same conventions as `forma.recipe`: validate → preview → approve → report.

## Library vs. active schedule
- **Library:** every workout available to you. Imports land here and **never change your schedule on their own**.
- **Programs:** weekly or multi-week schedules (1–52 weeks). The built-in *FORMA Base Program* is your Sun–Thu plan.
- **Active program:** exactly one at a time; it decides what's scheduled each day. In the preview you can:
  - tick **Make this my active program** for an imported program (start this week or next week), or
  - pick a weekday under **Add to my schedule** for a single workout.
- Editing the built-in program saves a copy called *My program*; the built-in one is never changed.
- Switching programs, updating workouts or deleting programs never deletes past sessions, sets, weights, reps or notes.

## Workout fields
| Field | Required | Notes |
|---|---|---|
| `id` | yes | Unique. Reusing an id updates that workout (only after approval). Built-in ids import as `<id>-imported`. |
| `name` | yes | |
| `category` | yes | Upper Body, Lower Body, Legs, Full Body, Push, Pull, Core, Cardio, Mobility, Conditioning, Other (others kept as written). |
| `difficulty` | | Beginner / Intermediate / Advanced (default Beginner, with a warning). |
| `duration_minutes` | | 5–240; estimated if missing. |
| `goal` / `goals`, `tags`, `notes`, `focus` | | |
| `progression` | | Default for all exercises: `{ "type": "double_progression" \| "linear" \| "none", "increment_lb": 5 }` |
| `exercises[]` | yes | See below. |

## Exercise fields
| Field | Required | Notes |
|---|---|---|
| `id` | | Stable id inside the workout (default: from the name). Keeps history aligned across updates. |
| `name` | yes | Matched to FORMA's exercise library by name, e.g. "Leg Press", "Goblet Squat". Unknown names become new exercises. |
| `sets` | yes | 1–10 |
| `reps` | yes* | `"12"`, `"10-12"`, `"AMRAP"`, or a time like `"45 s"`. *Or give a duration instead. |
| `duration_minutes` / `duration_seconds` / `duration` | yes* | For cardio and holds. |
| `rest_seconds` | | 0–600; default 60 with a warning. |
| `alternatives` | | Names or `{ "name", "instructions", "equipment" }`. They become the approved swaps. Without them, library exercises with the same movement pattern are offered. |
| `equipment`, `instructions`, `muscle_groups`, `type` (strength/cardio/mobility/core/balance), `tracks_weight`, `increment_lb`, `progression` | | |

## Program fields
| Field | Required | Notes |
|---|---|---|
| `id`, `name` | yes | |
| `weeks` | | 1–52 (default 1). After the last week the program repeats from week 1. |
| `schedule[]` | yes | `{ "day": "Sunday", "workout": "<id>" }`, or `{ "day": "Friday", "rest": true, "label": "Shabbos" }`. Add `"optional": true` for optional days and `"week": 5` to override one week (omit `week` or use `"all"` for every week). Referenced workouts must be in the file or already in FORMA. |

Validation reports exact paths (`workouts[2].exercises[1].reps`). Unknown fields are reported, and each item's original JSON is kept.
Export (Workout → Import → Export, or a workout or program's **Export JSON**) writes the same format, so programs move between phones.

## Prompt for ChatGPT
```
When I ask for workouts or a training program, reply ONLY with JSON in the FORMA Workout Import v1 format:

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
- Training days are Sunday–Thursday; Friday is rest for Shabbos; Saturday is optional (after Shabbos).
```
