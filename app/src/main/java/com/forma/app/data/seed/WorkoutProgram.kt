package com.forma.app.data.seed

import java.time.DayOfWeek

enum class ExerciseKind { STRENGTH, CARDIO, MOBILITY, CORE, BALANCE }

/** A movement in the library. [pattern] groups exercises that can substitute for one another. */
data class Exercise(
    val key: String,
    val name: String,
    val pattern: String,
    val kind: ExerciseKind,
    val instruction: String,
    val tracksWeight: Boolean = kind == ExerciseKind.STRENGTH,
    val incrementLb: Double = 5.0,
)

/** One slot in a day's program. */
data class Slot(
    val key: String,
    val exerciseKey: String,
    val sets: Int,
    /** Display prescription, e.g. "10–12" or "20–25 min". */
    val target: String,
    val repRange: IntRange? = null,
    val restSeconds: Int,
    /** Timed slots log minutes (cardio) or seconds (holds). */
    val timed: Boolean = false,
    val met: Double = 4.5,
)

data class WorkoutDay(
    val key: String,
    val day: DayOfWeek,
    val title: String,
    val focus: String,
    val optional: Boolean = false,
    val rest: Boolean = false,
    val estimatedMinutes: Int,
    val met: Double,
    val notes: String,
    val slots: List<Slot>,
)

object ExerciseLibrary {
    private val list = listOf(
        // Horizontal press
        Exercise("chest_press_machine", "Chest press machine", "horizontal_press", ExerciseKind.STRENGTH,
            "Set the handles at mid-chest. Press forward without locking the elbows, then lower for 2–3 seconds."),
        Exercise("db_bench_press", "Dumbbell bench press", "horizontal_press", ExerciseKind.STRENGTH,
            "Feet planted, shoulder blades pinched. Lower the dumbbells to chest level, press up and slightly in."),
        Exercise("cable_chest_press", "Standing cable chest press", "horizontal_press", ExerciseKind.STRENGTH,
            "Staggered stance, cables at chest height. Press forward and together; keep ribs down."),
        Exercise("pushup_incline", "Incline push-up (bench or Smith bar)", "horizontal_press", ExerciseKind.STRENGTH,
            "Hands on a bench, body in one straight line. Lower chest to the edge, press away.", tracksWeight = false),
        // Incline press
        Exercise("incline_chest_press", "Incline chest press", "incline_press", ExerciseKind.STRENGTH,
            "Machine or dumbbells on a 30° bench. Press up over the upper chest; control the lowering."),
        Exercise("incline_db_press", "Incline dumbbell press", "incline_press", ExerciseKind.STRENGTH,
            "Bench at 30°. Lower dumbbells to upper chest with elbows at ~45°, press back up."),
        Exercise("smith_incline_press", "Smith machine incline press", "incline_press", ExerciseKind.STRENGTH,
            "Bench at 30° under the bar. Lower to upper chest, press to just short of lockout."),
        // Vertical pull
        Exercise("lat_pulldown", "Lat pulldown", "vertical_pull", ExerciseKind.STRENGTH,
            "Grip just outside shoulders. Pull the bar to the upper chest, driving elbows down; return slowly."),
        Exercise("assisted_pullup", "Assisted pull-up machine", "vertical_pull", ExerciseKind.STRENGTH,
            "Choose enough assistance for clean reps. Pull chin over the bar, lower fully. Log the assistance weight."),
        Exercise("neutral_pulldown", "Neutral-grip pulldown", "vertical_pull", ExerciseKind.STRENGTH,
            "V-handle or neutral bar. Pull to the upper chest, elbows tight to the sides."),
        Exercise("single_arm_pulldown", "Single-arm cable pulldown", "vertical_pull", ExerciseKind.STRENGTH,
            "Kneel or sit facing the cable. Pull the handle down to the shoulder, one side at a time."),
        // Horizontal pull
        Exercise("seated_cable_row", "Seated cable row", "horizontal_pull", ExerciseKind.STRENGTH,
            "Sit tall, slight knee bend. Row the handle to the lower ribs, squeeze the shoulder blades, return slowly."),
        Exercise("chest_supported_row", "Chest-supported row machine", "horizontal_pull", ExerciseKind.STRENGTH,
            "Chest against the pad. Pull elbows back past the torso, pause, lower under control."),
        Exercise("one_arm_db_row", "One-arm dumbbell row", "horizontal_pull", ExerciseKind.STRENGTH,
            "Knee and hand on a bench, back flat. Row the dumbbell to the hip, lower fully."),
        // Vertical press
        Exercise("seated_shoulder_press", "Seated shoulder press", "vertical_press", ExerciseKind.STRENGTH,
            "Machine or dumbbells, back supported. Press overhead without arching; lower to ear level."),
        Exercise("db_shoulder_press", "Seated dumbbell shoulder press", "vertical_press", ExerciseKind.STRENGTH,
            "Bench upright. Press the dumbbells up and slightly in; lower to shoulder height."),
        Exercise("landmine_press", "Half-kneeling landmine press", "vertical_press", ExerciseKind.STRENGTH,
            "Press the bar end up and forward from the shoulder. Shoulder-friendly alternative."),
        // Lateral delts / rear delts
        Exercise("db_lateral_raise", "Dumbbell lateral raise", "lateral_raise", ExerciseKind.STRENGTH,
            "Slight forward lean, soft elbows. Raise to shoulder height leading with the elbows; lower slowly.",
            incrementLb = 2.5),
        Exercise("cable_lateral_raise", "Cable lateral raise", "lateral_raise", ExerciseKind.STRENGTH,
            "Cable at hand height behind you. Raise out to the side to shoulder height.", incrementLb = 2.5),
        Exercise("machine_lateral_raise", "Lateral raise machine", "lateral_raise", ExerciseKind.STRENGTH,
            "Pads on the outer arms. Raise to shoulder height, pause, lower for 2 seconds.", incrementLb = 2.5),
        Exercise("face_pull", "Face pull", "rear_delt", ExerciseKind.STRENGTH,
            "Rope at eye height. Pull towards the face, splitting the rope, elbows high; squeeze the rear delts.",
            incrementLb = 2.5),
        Exercise("reverse_pec_deck", "Reverse pec deck", "rear_delt", ExerciseKind.STRENGTH,
            "Face the pad, arms long. Sweep the handles back to shoulder line; control the return.", incrementLb = 2.5),
        Exercise("band_pull_apart", "Band pull-apart", "rear_delt", ExerciseKind.STRENGTH,
            "Arms straight at shoulder height. Pull the band apart to the chest, pause, return.", tracksWeight = false),
        // Biceps
        Exercise("db_biceps_curl", "Dumbbell biceps curl", "biceps", ExerciseKind.STRENGTH,
            "Elbows pinned at the sides. Curl with palms up, squeeze, lower for 2–3 seconds.", incrementLb = 2.5),
        Exercise("hammer_curl", "Hammer curl", "biceps", ExerciseKind.STRENGTH,
            "Neutral grip (thumbs up). Curl without swinging; works biceps and forearm (brachialis).", incrementLb = 2.5),
        Exercise("cable_curl", "Cable curl", "biceps", ExerciseKind.STRENGTH,
            "Straight bar on a low cable. Curl up keeping elbows still; constant tension.", incrementLb = 2.5),
        Exercise("rope_hammer_curl", "Rope cable hammer curl", "biceps", ExerciseKind.STRENGTH,
            "Rope on a low cable, thumbs up. Curl to the shoulders, lower slowly.", incrementLb = 2.5),
        Exercise("preacher_curl_machine", "Preacher curl machine", "biceps", ExerciseKind.STRENGTH,
            "Armpits snug to the pad. Curl through a full range; don't bounce at the bottom.", incrementLb = 2.5),
        // Triceps
        Exercise("cable_triceps_pushdown", "Cable triceps pushdown", "triceps", ExerciseKind.STRENGTH,
            "Elbows at the sides. Push the bar down to full extension, return to 90°.", incrementLb = 2.5),
        Exercise("rope_pushdown", "Rope pushdown", "triceps", ExerciseKind.STRENGTH,
            "Push down and split the rope at the bottom; keep upper arms still.", incrementLb = 2.5),
        Exercise("overhead_cable_extension", "Overhead cable extension", "triceps", ExerciseKind.STRENGTH,
            "Face away from the cable, rope overhead. Extend the elbows forward and up.", incrementLb = 2.5),
        Exercise("db_overhead_extension", "Seated dumbbell overhead extension", "triceps", ExerciseKind.STRENGTH,
            "Hold one dumbbell overhead with both hands; lower behind the head, extend.", incrementLb = 2.5),
        // Squat / quad
        Exercise("leg_press", "Leg press", "squat", ExerciseKind.STRENGTH,
            "Feet shoulder-width mid-platform. Lower until knees are ~90°, keep the lower back on the pad, press up.",
            incrementLb = 10.0),
        Exercise("goblet_squat", "Goblet squat", "squat", ExerciseKind.STRENGTH,
            "Hold a dumbbell at the chest. Sit down between the heels, chest tall, then drive up.", incrementLb = 5.0),
        Exercise("hack_squat", "Hack squat machine", "squat", ExerciseKind.STRENGTH,
            "Shoulders under pads, feet mid-plate. Lower under control to a comfortable depth, press up.",
            incrementLb = 10.0),
        Exercise("box_squat", "Box squat to bench", "squat", ExerciseKind.STRENGTH,
            "Squat back to lightly touch a bench, stand up without rocking. Great for building depth.",
            incrementLb = 5.0),
        Exercise("leg_extension", "Leg extension", "quad_isolation", ExerciseKind.STRENGTH,
            "Pad on the lower shin. Extend fully, squeeze the quads for 1 second, lower slowly.", incrementLb = 5.0),
        Exercise("split_squat", "Split squat", "quad_isolation", ExerciseKind.STRENGTH,
            "Long stance, back heel up. Lower the back knee towards the floor, drive through the front foot.",
            incrementLb = 5.0),
        Exercise("step_up", "Dumbbell step-up", "quad_isolation", ExerciseKind.STRENGTH,
            "Knee-height box. Step up driving through the whole front foot; control the step down.",
            incrementLb = 5.0),
        Exercise("wall_sit", "Wall sit (ski hold)", "quad_isolation", ExerciseKind.STRENGTH,
            "Back against the wall, thighs near parallel. Hold — the classic ski-quad endurance drill.",
            tracksWeight = false),
        // Hinge
        Exercise("romanian_deadlift", "Romanian deadlift (dumbbell)", "hinge", ExerciseKind.STRENGTH,
            "Soft knees, push hips back with a flat back until you feel the hamstrings; stand by squeezing the glutes.",
            incrementLb = 5.0),
        Exercise("cable_pull_through", "Cable pull-through", "hinge", ExerciseKind.STRENGTH,
            "Rope between the legs from a low cable. Hinge back, then drive the hips forward to stand tall.",
            incrementLb = 5.0),
        Exercise("back_extension", "45° back extension", "hinge", ExerciseKind.STRENGTH,
            "Hips on the pad. Hinge down with a neutral spine, rise until the body is straight.", incrementLb = 5.0),
        // Hamstring curl
        Exercise("seated_leg_curl", "Seated leg curl", "knee_flexion", ExerciseKind.STRENGTH,
            "Lap pad snug. Curl the heels under the seat, pause, return slowly.", incrementLb = 5.0),
        Exercise("lying_leg_curl", "Lying leg curl", "knee_flexion", ExerciseKind.STRENGTH,
            "Hips pressed into the pad. Curl the heels towards the glutes, lower over 2–3 seconds.", incrementLb = 5.0),
        Exercise("ball_leg_curl", "Stability-ball leg curl", "knee_flexion", ExerciseKind.STRENGTH,
            "Heels on a ball, hips lifted. Roll the ball in by bending the knees, keep hips high.",
            tracksWeight = false),
        // Calves
        Exercise("standing_calf_raise", "Standing calf raise", "calf", ExerciseKind.STRENGTH,
            "Balls of the feet on the edge. Full stretch at the bottom, rise high, pause 1 second.", incrementLb = 10.0),
        Exercise("seated_calf_raise", "Seated calf raise", "calf", ExerciseKind.STRENGTH,
            "Pad on the lower thighs. Lower the heels fully, press up and pause.", incrementLb = 10.0),
        Exercise("leg_press_calf_raise", "Calf press on leg press", "calf", ExerciseKind.STRENGTH,
            "Balls of the feet on the bottom edge of the platform; legs nearly straight. Press through the toes.",
            incrementLb = 10.0),
        // Glutes
        Exercise("glute_bridge", "Glute bridge", "glute_bridge", ExerciseKind.STRENGTH,
            "On your back, feet flat. Drive hips up until in line with knees and shoulders; squeeze 1 second.",
            incrementLb = 10.0),
        Exercise("hip_thrust_machine", "Hip thrust (machine or bench)", "glute_bridge", ExerciseKind.STRENGTH,
            "Upper back on a bench, load across the hips. Drive up to a flat top position; chin tucked.",
            incrementLb = 10.0),
        Exercise("single_leg_bridge", "Single-leg glute bridge", "glute_bridge", ExerciseKind.STRENGTH,
            "One foot planted, other leg extended. Bridge up keeping hips level.", tracksWeight = false),
        // Cardio
        Exercise("incline_treadmill_walk", "Incline treadmill walk", "cardio", ExerciseKind.CARDIO,
            "Incline 6–10%, 2.8–3.3 mph. Don't hold the rails. Conversational but working effort."),
        Exercise("stationary_bike", "Stationary bike", "cardio", ExerciseKind.CARDIO,
            "Seat at hip height, slight knee bend at the bottom. Steady moderate resistance, 70–90 rpm."),
        Exercise("elliptical", "Elliptical", "cardio", ExerciseKind.CARDIO,
            "Upright posture, push through the heels. Low impact on knees and back."),
        Exercise("rowing_machine", "Rowing machine", "cardio", ExerciseKind.CARDIO,
            "Legs → body → arms, then reverse. Easy, smooth strokes ~20–24 per minute."),
        Exercise("stair_climber", "Stair climber (easy pace)", "cardio", ExerciseKind.CARDIO,
            "Slow steady pace, light hand contact only. Excellent for ski-specific leg endurance."),
        Exercise("outdoor_walk", "Brisk walk", "cardio", ExerciseKind.CARDIO,
            "Comfortable brisk pace. Ideal for the optional post-Shabbos session."),
        // Core
        Exercise("plank", "Plank", "anti_extension", ExerciseKind.CORE,
            "Forearms under shoulders, body in a straight line. Squeeze glutes and brace; hold.", tracksWeight = false),
        Exercise("incline_plank", "Incline plank (hands on bench)", "anti_extension", ExerciseKind.CORE,
            "Easier plank with hands on a bench. Same straight-line position.", tracksWeight = false),
        Exercise("dead_bug", "Dead bug", "anti_extension", ExerciseKind.CORE,
            "Low back pressed to the floor. Slowly extend opposite arm and leg, return, alternate.",
            tracksWeight = false),
        Exercise("bird_dog", "Bird dog", "anti_extension", ExerciseKind.CORE,
            "On hands and knees, reach opposite arm and leg long without rotating the hips. Pause, switch.",
            tracksWeight = false),
        Exercise("pallof_press", "Pallof press", "anti_rotation", ExerciseKind.CORE,
            "Side-on to a cable at chest height. Press the handle straight out and resist the twist; return.",
            incrementLb = 2.5, tracksWeight = true),
        Exercise("side_plank", "Side plank", "anti_rotation", ExerciseKind.CORE,
            "Elbow under shoulder, hips high in a straight line. Knees down to make it easier.",
            tracksWeight = false),
        Exercise("suitcase_carry", "Suitcase carry", "anti_rotation", ExerciseKind.CORE,
            "Heavy dumbbell in one hand; walk tall without leaning. Switch hands.", incrementLb = 5.0,
            tracksWeight = true),
        // Mobility & balance
        Exercise("ankle_mobility", "Ankle mobility", "ankle_mobility", ExerciseKind.MOBILITY,
            "Knee-to-wall rocks (10 per side), then calf stretch against the wall 30 s per side. Repeat. " +
                "Ski boots demand good ankle bend."),
        Exercise("calf_stretch_wall", "Wall calf stretch", "ankle_mobility", ExerciseKind.MOBILITY,
            "Hands on the wall, back leg straight, heel down. 30–45 s straight-knee, then bent-knee."),
        Exercise("hip_flexor_mobility", "Half-kneeling hip flexor stretch", "hip_flexor", ExerciseKind.MOBILITY,
            "Back knee down, tuck the pelvis and squeeze the back glute, shift forward gently. 45 s per side."),
        Exercise("standing_hip_flexor", "Standing hip flexor stretch", "hip_flexor", ExerciseKind.MOBILITY,
            "Split stance, back heel up, tuck the pelvis and lean slightly forward. 45 s per side."),
        Exercise("hamstring_mobility", "Hamstring mobility (strap)", "hamstring", ExerciseKind.MOBILITY,
            "On your back, raise a straight leg with a strap until a gentle stretch. 45 s per side, breathe slowly."),
        Exercise("standing_hamstring", "Standing hamstring stretch", "hamstring", ExerciseKind.MOBILITY,
            "Heel on a low step, toes up, hinge forward with a flat back. 45 s per side."),
        Exercise("hip_90_90", "90/90 hip switches", "hip_mobility", ExerciseKind.MOBILITY,
            "Seated, both knees bent at 90°. Rotate the knees side to side slowly, staying tall."),
        Exercise("worlds_greatest", "World's greatest stretch", "hip_mobility", ExerciseKind.MOBILITY,
            "Lunge, hand inside the front foot, rotate the top arm to the ceiling. 5 per side."),
        Exercise("balance_single_leg", "Single-leg balance", "balance", ExerciseKind.BALANCE,
            "Stand on one leg, slight knee bend (ski stance). 30 s per leg; close your eyes to progress."),
        Exercise("balance_tandem", "Tandem stance / heel-to-toe walk", "balance", ExerciseKind.BALANCE,
            "Feet in a line, heel to toe. Hold 30 s or walk slowly 10 steps."),
        Exercise("balance_reach", "Single-leg reach (bodyweight RDL)", "balance", ExerciseKind.BALANCE,
            "On one leg, hinge and reach forward, return tall. 8 per side. Builds ankle and hip stability."),
    )

    val byKey: Map<String, Exercise> = list.associateBy { it.key }

    fun get(key: String): Exercise = byKey[key] ?: error("Unknown exercise $key")

    /** Same movement pattern, excluding the exercise itself. */
    fun substitutesFor(key: String): List<Exercise> {
        val e = byKey[key] ?: return emptyList()
        return list.filter { it.pattern == e.pattern && it.key != key }
    }

    val all: List<Exercise> get() = list
}

object WorkoutProgram {
    private fun s(day: String, i: Int, ex: String, sets: Int, target: String, range: IntRange?, rest: Int,
                  timed: Boolean = false, met: Double = 4.5) = Slot("${day}_$i", ex, sets, target, range, rest, timed, met)

    val days: List<WorkoutDay> = listOf(
        WorkoutDay(
            "SUN", DayOfWeek.SUNDAY, "Upper Body A + Cardio", "Chest · back · shoulders · arms",
            estimatedMinutes = 55, met = 4.5,
            notes = "Leave 2–3 reps in reserve on each set. Add weight only when every set reaches the top of the range.",
            slots = listOf(
                s("SUN", 1, "chest_press_machine", 3, "10–12", 10..12, 90),
                s("SUN", 2, "lat_pulldown", 3, "10–12", 10..12, 90),
                s("SUN", 3, "seated_shoulder_press", 2, "10–12", 10..12, 90),
                s("SUN", 4, "db_biceps_curl", 3, "10–12", 10..12, 60),
                s("SUN", 5, "cable_triceps_pushdown", 2, "12", 12..12, 60),
                s("SUN", 6, "incline_treadmill_walk", 1, "10–15 min", null, 0, timed = true, met = 5.0),
            ),
        ),
        WorkoutDay(
            "MON", DayOfWeek.MONDAY, "Legs + Ski Prep", "Quads · hamstrings · glutes · calves · ankles",
            estimatedMinutes = 55, met = 5.0,
            notes = "Controlled tempo builds the eccentric strength skiing needs. Rest a full 2 minutes on leg press.",
            slots = listOf(
                s("MON", 1, "leg_press", 3, "10–12", 10..12, 120),
                s("MON", 2, "seated_leg_curl", 3, "12", 12..12, 75),
                s("MON", 3, "leg_extension", 2, "12", 12..12, 60),
                s("MON", 4, "standing_calf_raise", 3, "15", 15..15, 45),
                s("MON", 5, "glute_bridge", 3, "12", 12..12, 60),
                s("MON", 6, "ankle_mobility", 1, "5 min", null, 0, timed = true, met = 2.3),
            ),
        ),
        WorkoutDay(
            "TUE", DayOfWeek.TUESDAY, "Cardio + Mobility", "Aerobic base · hips · hamstrings · balance",
            estimatedMinutes = 50, met = 4.5,
            notes = "Lower-intensity day. You should be able to hold a conversation throughout.",
            slots = listOf(
                s("TUE", 1, "incline_treadmill_walk", 1, "20–25 min", null, 0, timed = true, met = 5.0),
                s("TUE", 2, "stationary_bike", 1, "10–15 min", null, 0, timed = true, met = 5.5),
                s("TUE", 3, "hip_flexor_mobility", 2, "45 s / side", null, 15, timed = true, met = 2.3),
                s("TUE", 4, "hamstring_mobility", 2, "45 s / side", null, 15, timed = true, met = 2.3),
                s("TUE", 5, "ankle_mobility", 1, "5 min", null, 0, timed = true, met = 2.3),
                s("TUE", 6, "balance_single_leg", 3, "30 s / leg", null, 20, timed = true, met = 2.5),
            ),
        ),
        WorkoutDay(
            "WED", DayOfWeek.WEDNESDAY, "Upper Body B + Arms", "Back · shoulders · biceps · core",
            estimatedMinutes = 55, met = 4.5,
            notes = "Shoulder and arm emphasis. Keep lateral raises light and strict.",
            slots = listOf(
                s("WED", 1, "seated_cable_row", 3, "10–12", 10..12, 90),
                s("WED", 2, "incline_chest_press", 3, "10–12", 10..12, 90),
                s("WED", 3, "db_lateral_raise", 3, "12–15", 12..15, 60),
                s("WED", 4, "hammer_curl", 3, "10–12", 10..12, 60),
                s("WED", 5, "face_pull", 2, "12–15", 12..15, 60),
                s("WED", 6, "plank", 3, "20–40 s", null, 45, timed = true, met = 3.0),
            ),
        ),
        WorkoutDay(
            "THU", DayOfWeek.THURSDAY, "Legs + Core", "Squat · hinge · hamstrings · anti-rotation",
            estimatedMinutes = 55, met = 5.0,
            notes = "Own the movement before adding load. Brace your core on every rep.",
            slots = listOf(
                s("THU", 1, "goblet_squat", 3, "10", 10..10, 120),
                s("THU", 2, "romanian_deadlift", 3, "10", 10..10, 120),
                s("THU", 3, "lying_leg_curl", 2, "12", 12..12, 75),
                s("THU", 4, "standing_calf_raise", 3, "15", 15..15, 45),
                s("THU", 5, "dead_bug", 3, "8–10 / side", 8..10, 45),
                s("THU", 6, "pallof_press", 2, "12 / side", 12..12, 45),
            ),
        ),
        WorkoutDay(
            "FRI", DayOfWeek.FRIDAY, "Rest · Shabbos", "Recovery", rest = true,
            estimatedMinutes = 0, met = 0.0,
            notes = "No workout scheduled. Recovery is when the adaptation happens.",
            slots = emptyList(),
        ),
        WorkoutDay(
            "SAT", DayOfWeek.SATURDAY, "Optional · After Shabbos", "Light cardio · mobility · balance",
            optional = true, estimatedMinutes = 35, met = 3.5,
            notes = "Entirely optional. Keep it easy — a walk or gentle ride, then mobility.",
            slots = listOf(
                s("SAT", 1, "outdoor_walk", 1, "20–30 min", null, 0, timed = true, met = 3.8),
                s("SAT", 2, "hip_90_90", 1, "5 min", null, 0, timed = true, met = 2.3),
                s("SAT", 3, "ankle_mobility", 1, "5 min", null, 0, timed = true, met = 2.3),
                s("SAT", 4, "balance_single_leg", 3, "30 s / leg", null, 20, timed = true, met = 2.5),
            ),
        ),
    )

    fun forDay(d: DayOfWeek): WorkoutDay = days.first { it.day == d }
    fun byKey(k: String): WorkoutDay? = days.firstOrNull { it.key == k }
    val requiredPerWeek: Int get() = days.count { !it.rest && !it.optional }
}
