package com.tanimul.android_template_kotlin

import android.content.Context
import android.content.SharedPreferences

object DataManager {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        // অ্যাপের ডাটা লোকালি সেভ রাখার জন্য SharedPreferences
        prefs = context.getSharedPreferences("RasFocusData", Context.MODE_PRIVATE)
    }

    // ==========================================
    // 🔴 CORE TOGGLES (Default: false)
    // ==========================================
    var isFocusActive: Boolean
        get() = prefs.getBoolean("isFocusActive", false)
        set(value) = prefs.edit().putBoolean("isFocusActive", value).apply()

    var isAdultFocusActive: Boolean
        get() = prefs.getBoolean("isAdultFocusActive", false)
        set(value) = prefs.edit().putBoolean("isAdultFocusActive", value).apply()

    var blockSettingsAndUninstall: Boolean
        get() = prefs.getBoolean("blockSettingsAndUninstall", false)
        set(value) = prefs.edit().putBoolean("blockSettingsAndUninstall", value).apply()

    var is24HourLockActive: Boolean
        get() = prefs.getBoolean("is24HourLockActive", false)
        set(value) = prefs.edit().putBoolean("is24HourLockActive", value).apply()

    var isPeriodicPopupsActive: Boolean
        get() = prefs.getBoolean("isPeriodicPopupsActive", false)
        set(value) = prefs.edit().putBoolean("isPeriodicPopupsActive", value).apply()

    // ==========================================
    // 🔵 DEEP STUDY SPECIFIC SETTINGS
    // ==========================================
    var isDeepStudyStrict: Boolean
        get() = prefs.getBoolean("isDeepStudyStrict", false)
        set(value) = prefs.edit().putBoolean("isDeepStudyStrict", value).apply()

    var dsFocusMin: Int
        get() = prefs.getInt("dsFocusMin", 25) // ডিফল্ট ২৫ মিনিট
        set(value) = prefs.edit().putInt("dsFocusMin", value).apply()

    var dsRestMin: Int
        get() = prefs.getInt("dsRestMin", 5) // ডিফল্ট ৫ মিনিট
        set(value) = prefs.edit().putInt("dsRestMin", value).apply()

    var dsTotalSessions: Int
        get() = prefs.getInt("dsTotalSessions", 4) // ডিফল্ট ৪ সেশন
        set(value) = prefs.edit().putInt("dsTotalSessions", value).apply()

    var dsKeepBlockingInBreak: Boolean
        get() = prefs.getBoolean("dsKeepBlockingInBreak", false)
        set(value) = prefs.edit().putBoolean("dsKeepBlockingInBreak", value).apply()

    // ==========================================
    // Others Data & Settings
    // ==========================================
    var lock24hEndTime: Long
        get() = prefs.getLong("lock24hEndTime", 0L)
        set(value) = prefs.edit().putLong("lock24hEndTime", value).apply()

    var controlMode: Int
        get() = prefs.getInt("controlMode", 0) // 0 = Self, 1 = Friend
        set(value) = prefs.edit().putInt("controlMode", value).apply()

    var simpleBlockMode: Int
        get() = prefs.getInt("simpleBlockMode", 1) // 1 = Block Mode
        set(value) = prefs.edit().putInt("simpleBlockMode", value).apply()

    var adultReligion: Int
        get() = prefs.getInt("adultReligion", 0) // 0 = Muslim
        set(value) = prefs.edit().putInt("adultReligion", value).apply()

    var adultLanguage: Int
        get() = prefs.getInt("adultLanguage", 0) // 0 = Bangla
        set(value) = prefs.edit().putInt("adultLanguage", value).apply()

    var showQuotes: Boolean
        get() = prefs.getBoolean("showQuotes", true)
        set(value) = prefs.edit().putBoolean("showQuotes", value).apply()

    var totalBlockedCount: Int
        get() = prefs.getInt("totalBlockedCount", 0)
        set(value) = prefs.edit().putInt("totalBlockedCount", value).apply()

    var cleanStreakDays: Int
        get() = prefs.getInt("cleanStreakDays", 12)
        set(value) = prefs.edit().putInt("cleanStreakDays", value).apply()

    // ==========================================
    // 🟡 LISTS (General Blocks vs Deep Study Allows)
    // ==========================================
    var userCustomAdultKeywords: List<String>
        get() = prefs.getStringSet("userCustomAdultKeywords", emptySet())?.toList() ?: emptyList()
        set(value) = prefs.edit().putStringSet("userCustomAdultKeywords", value.toSet()).apply()

    // সাধারণ ব্লক লিস্ট (Blacklist)
    var userWebList: List<String>
        get() = prefs.getStringSet("userWebList", setOf("facebook.com", "tiktok.com", "instagram.com"))?.toList() ?: listOf("facebook.com", "tiktok.com", "instagram.com")
        set(value) = prefs.edit().putStringSet("userWebList", value.toSet()).apply()

    var userAppList: List<String>
        get() = prefs.getStringSet("userAppList", setOf("com.whatsapp", "com.facebook.katana", "com.zhiliaoapp.musically"))?.toList() ?: listOf("com.whatsapp", "com.facebook.katana", "com.zhiliaoapp.musically")
        set(value) = prefs.edit().putStringSet("userAppList", value.toSet()).apply()

    // ডিপ স্টাডি অ্যালাউ লিস্ট (Whitelist) - এটি সম্পূর্ণ আলাদা!
    var dsAllowWebList: List<String>
        get() = prefs.getStringSet("dsAllowWebList", setOf("wikipedia.org", "github.com", "stackoverflow.com"))?.toList() ?: listOf("wikipedia.org", "github.com", "stackoverflow.com")
        set(value) = prefs.edit().putStringSet("dsAllowWebList", value.toSet()).apply()

    var dsAllowAppList: List<String>
        get() = prefs.getStringSet("dsAllowAppList", setOf("com.android.calculator2", "com.google.android.calendar"))?.toList() ?: listOf("com.android.calculator2", "com.google.android.calendar")
        set(value) = prefs.edit().putStringSet("dsAllowAppList", value.toSet()).apply()
}
