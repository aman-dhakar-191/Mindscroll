package io.github.amandhakar.mindscroll.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Many phone makers (Xiaomi, Oppo, Vivo, Huawei, OnePlus, Samsung) have their own "auto-start" or
 * "sleeping apps" list on top of Android's battery setting. An app that is not allowed there is
 * stopped in the background and not started again, even with battery set to "No restrictions".
 * For an accessibility service this shows as "Not working" next to its switch.
 *
 * There is no way to ask whether it is allowed, and no standard way to open the page, so this opens
 * the maker's page when it can be found, and App info otherwise. The user turns it on and says so.
 */
object Autostart {
    class Oem(val name: String, val pages: List<ComponentName>, val where: String)

    private fun page(pkg: String, cls: String) = ComponentName(pkg, cls)

    private val xiaomi = Oem(
        "Xiaomi",
        listOf(page("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        "Find Mindscroll in the list and switch Autostart on.",
    )
    private val oppo = Oem(
        "Oppo / Realme",
        listOf(
            page("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            page("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            page("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ),
        "Find Mindscroll and allow auto-launch (Settings → Apps → Auto launch).",
    )
    private val vivo = Oem(
        "Vivo / iQOO",
        listOf(
            page("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            page("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ),
        "Find Mindscroll and allow background start (Settings → Battery → Background power consumption).",
    )
    private val huawei = Oem(
        "Huawei / Honor",
        listOf(page("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        "Find Mindscroll and switch on Auto-launch and Run in background (Settings → Apps → App launch).",
    )
    private val oneplus = Oem(
        "OnePlus",
        listOf(page("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
        "Find Mindscroll and allow auto-launch.",
    )
    private val samsung = Oem(
        "Samsung",
        listOf(page("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
        "Under Battery → Background usage limits, make sure Mindscroll is not in Sleeping or Deep sleeping apps.",
    )

    /** The maker's details for this [manufacturer] or [brand], or null for a phone with no such list. */
    fun forPhone(manufacturer: String?, brand: String? = null): Oem? {
        val names = listOfNotNull(manufacturer, brand).map { it.lowercase() }
        fun has(vararg keys: String) = names.any { n -> keys.any { it in n } }
        return when {
            has("xiaomi", "redmi", "poco") -> xiaomi
            has("oppo", "realme") -> oppo
            has("vivo", "iqoo") -> vivo
            has("huawei", "honor") -> huawei
            has("oneplus") -> oneplus
            has("samsung") -> samsung
            else -> null
        }
    }

    /** This phone's maker details, or null. */
    val thisPhone: Oem? get() = forPhone(Build.MANUFACTURER, Build.BRAND)

    /** Whether setup should have an auto-start step on this phone. */
    val needed: Boolean get() = thisPhone != null

    /**
     * Opens the maker's auto-start page, or App info when that page is not there (makers move and
     * lock these pages, so each is tried and any refusal is expected).
     */
    fun open(context: Context) {
        val oem = thisPhone
        for (component in oem?.pages.orEmpty()) {
            val opened = runCatching {
                context.startActivity(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (opened) return
        }
        runCatching { context.startActivity(PermissionIntents.appInfo(context)) }
    }
}
