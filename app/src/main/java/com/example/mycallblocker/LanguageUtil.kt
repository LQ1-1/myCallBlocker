package com.example.mycallblocker

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LanguageUtil {
    private const val PREF_LANG = "app_language"

    // 支持的语言代码: ""=跟随系统, "en"=英文, "zh"=中文
    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_LANG, "") ?: ""
    }

    fun setLanguage(context: Context, langCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_LANG, langCode).apply()
    }

    fun attachBaseContext(context: Context): ContextWrapper {
        val langCode = getSavedLanguage(context)
        if (langCode.isEmpty()) return ContextWrapper(context)

        val locale = if (langCode == "zh") Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
        val config = context.resources.configuration

        Locale.setDefault(locale)
        config.setLocale(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        }

        val newContext = context.createConfigurationContext(config)
        return ContextWrapper(newContext)
    }
}