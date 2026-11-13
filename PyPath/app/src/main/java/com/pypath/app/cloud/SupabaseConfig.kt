package com.pypath.app.cloud

/**
 * Supabase project used for sign-in and cloud backup.
 *
 * The publishable key is designed to be shipped inside apps: it only identifies the project, and
 * every table is protected by Row Level Security (each signed-in user can reach only their own
 * `user_cloud_state` row). Service-role keys, database passwords and OAuth client secrets are never
 * part of the app.
 */
object SupabaseConfig {
    const val URL = "https://rkaifprdaryqkedekprk.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_xuDlLWD30e0-aWvlc3rA1Q_pTckdSnL"
    const val TABLE = "user_cloud_state"
}
