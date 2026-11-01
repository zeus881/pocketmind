package com.manjit.pocketmind.data.benchmark

/**
 * The 3 FIXED prompts. They never change, so results from different phones, models and app
 * versions are comparable. Short tests latency, medium tests steady decode speed, long tests
 * prefill (reading a long prompt) — the three things users feel.
 */
object BenchmarkPrompts {
    data class Prompt(val name: String, val text: String)

    const val SYSTEM_PROMPT = "You are a helpful assistant. Follow the instructions exactly."

    private val longContext = """
        Unmanned aerial vehicles, commonly called drones, combine lightweight frames, brushless
        motors, electronic speed controllers, a flight controller and a battery. The flight
        controller reads an inertial measurement unit hundreds of times per second. The IMU
        contains gyroscopes that measure rotation rate and accelerometers that measure linear
        acceleration. A control loop, usually PID based, compares the measured attitude with the
        desired attitude and changes each motor's speed to correct the error. GPS and barometers
        add position and altitude hold. Batteries are typically lithium polymer cells, chosen for
        their high power-to-weight ratio, but they limit flight time to roughly twenty to forty
        minutes for consumer drones. Regulations in many countries require registration, line of
        sight operation and altitude limits, and restrict flights near airports and crowds.
        Modern drones also run on-board computer vision for obstacle avoidance and tracking,
        which increasingly happens on low-power neural accelerators instead of in the cloud.
    """.trimIndent()

    val all = listOf(
        Prompt("short", "What is the capital of France? Answer in one sentence."),
        Prompt("medium", "Explain in about 100 words how a quadcopter drone stays stable in the air."),
        Prompt("long", "$longContext\n\nSummarize the text above in exactly 3 bullet points."),
    )
}
