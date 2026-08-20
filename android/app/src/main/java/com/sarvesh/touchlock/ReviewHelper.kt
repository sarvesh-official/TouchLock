package com.sarvesh.touchlock

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory

object ReviewHelper {

    private const val PREFS_NAME = "review_state"
    private const val KEY_SUCCESSFUL_ACTIONS = "successful_actions"
    private const val KEY_REVIEW_SHOWN = "review_shown"
    private const val TRIGGER_THRESHOLD = 5

    fun recordSuccessfulAction(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_SUCCESSFUL_ACTIONS, 0) + 1
        prefs.edit().putInt(KEY_SUCCESSFUL_ACTIONS, count).apply()
    }

    fun shouldPromptForReview(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_SUCCESSFUL_ACTIONS, 0)
        val shown = prefs.getBoolean(KEY_REVIEW_SHOWN, false)
        return count >= TRIGGER_THRESHOLD && !shown
    }

    fun promptReview(activity: Activity) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    manager.launchReviewFlow(activity, reviewInfo)
                        .addOnCompleteListener { markReviewShown(activity) }
                }
            }
        } catch (_: Exception) {
            // Silently fail — don't bother user if review API unavailable
        }
    }

    private fun markReviewShown(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_REVIEW_SHOWN, true).apply()
    }
}
