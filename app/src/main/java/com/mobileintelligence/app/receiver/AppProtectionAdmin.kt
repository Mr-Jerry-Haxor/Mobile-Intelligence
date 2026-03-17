package com.mobileintelligence.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Device Admin receiver that prevents unauthorized uninstallation
 * of the app when Device Admin is active.
 *
 * The user must deactivate Device Admin (which requires the NumLock PIN)
 * before they can uninstall, force-stop, or clear data of this app.
 */
class AppProtectionAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Uninstall protection enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Uninstall protection disabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling protection will allow the app to be uninstalled."
    }
}
