package com.sanjay.pocketmind.buildlogic

/**
 * The app's version, derived from a git tag such as `v2.1.0` or `v2.0.0-beta3`.
 *
 * Google Play needs two numbers for every upload:
 * - `versionName`: what users see ("2.1.0").
 * - `versionCode`: an integer that must be HIGHER than every earlier upload, or Play rejects it.
 *
 * Deriving both from the tag means a release is just `git tag v2.1.0 && git push --tags`; nobody
 * edits a number by hand (and forgets). The code packs the parts so that ordering is preserved:
 * ```
 *  versionCode = major·1 000 000 + minor·10 000 + patch·100 + stage
 *  stage: alphaN → N (1–29), betaN → 30+N (31–59), rcN → 60+N (61–98), final release → 99
 *  v2.0.0-alpha1 = 2 000 001  <  v2.0.0-beta1 = 2 000 031  <  v2.0.0-rc1 = 2 000 061  <  v2.0.0 = 2 000 099
 * ```
 * Limits: minor and patch ≤ 99, pre-release number within its band. Out-of-range tags are rejected
 * at build time instead of silently producing a code that sorts wrongly.
 */
data class AppVersion(val name: String, val code: Int) {
    companion object {
        /** Local builds without a release tag. Code 1 is valid but lower than any real release. */
        val DEVELOPMENT = AppVersion(name = "0.0.0-dev", code = 1)

        private val TAG = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)\.?(\d+))?$""")
        private const val MAX_PART = 99
        private const val FINAL_STAGE = 99
        private val STAGE_BASE = mapOf("alpha" to 0, "beta" to 30, "rc" to 60)
        private val STAGE_MAX = mapOf("alpha" to 29, "beta" to 29, "rc" to 38)

        /** Parses [tag]; null or blank means "no release tag" → [DEVELOPMENT]. */
        fun fromTag(tag: String?): AppVersion {
            val clean = tag?.trim().orEmpty()
            if (clean.isEmpty()) return DEVELOPMENT
            val match = requireNotNull(TAG.matchEntire(clean)) {
                "Version tag '$clean' must look like v1.2.3 or v1.2.3-beta2"
            }
            val (major, minor, patch) = match.destructured.toList().take(3).map(String::toInt)
            require(minor <= MAX_PART && patch <= MAX_PART) { "Minor and patch must be <= $MAX_PART in '$clean'" }
            val stageName = match.groupValues[4]
            val stage = if (stageName.isEmpty()) {
                FINAL_STAGE
            } else {
                val number = match.groupValues[5].toInt()
                require(number in 1..STAGE_MAX.getValue(stageName)) { "Pre-release number out of range in '$clean'" }
                STAGE_BASE.getValue(stageName) + number
            }
            val name = clean.removePrefix("v")
            return AppVersion(name = name, code = major * 1_000_000 + minor * 10_000 + patch * 100 + stage)
        }
    }
}
