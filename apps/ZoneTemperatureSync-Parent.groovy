/**
 * Zone Temperature Sync - Parent App
 *
 * Container app that manages one child app instance per zone.
 * Install this first; each zone is configured by adding a child app
 * ("Zone Temperature Sync Child") from this parent's settings page.
 *
 * Installation order:
 *   1. Install the "Zone Temperature Sync Thermostat" driver
 *   2. Create one Virtual Device per zone using that driver
 *   3. Install this parent app (Apps → Add User App)
 *   4. Add a child zone app for each zone
 */

definition(
    name:        "Zone Temperature Sync",
    namespace:   "jamesfrank",
    author:      "Zone Temperature Sync",
    description: "Mirrors temperature and humidity sensor readings to virtual thermostat zones for HomeKit display.",
    category:    "Convenience",
    singleInstance: true,
    iconUrl:     "",
    iconX2Url:   ""
)

preferences {
    page(name: "mainPage")
}

// ──────────────────────────────────────────────────────────────────
// Pages
// ──────────────────────────────────────────────────────────────────

def mainPage() {
    dynamicPage(name: "mainPage", title: "Zone Temperature Sync", install: true, uninstall: true) {

        section("<h2>Zones</h2>") {
            paragraph "Each zone averages readings from one or more sensors and mirrors the result to a virtual thermostat device visible in HomeKit."
            app(
                name:    "zones",
                appName: "Zone Temperature Sync Child",
                namespace: "jamesfrank",
                title:   "<b>➕ Add a New Zone</b>",
                multiple: true
            )
        }

        section("<h2>About</h2>") {
            paragraph(
                "<b>How it works:</b><br>" +
                "• Each zone averages temperature and humidity from all linked sensors that are online.<br>" +
                "• A sensor is considered <i>offline</i> if its health status is 'offline' OR it hasn't reported within the configured timeout window.<br>" +
                "• The minimum battery across all sensors is copied to the virtual thermostat (so HomeKit low-battery alerts fire). If any sensor is offline, battery is set to 0% to force an alert.<br>" +
                "• When no valid temperature sensors exist, the thermostat mode is set to 'off' so HomeKit can visually indicate the zone has no data.<br>" +
                "<br>" +
                "<b>Required driver:</b> <i>Zone Temperature Sync Thermostat</i> - create one virtual device per zone using this driver before configuring zones here."
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────
// Lifecycle (parent app has no logic of its own)
// ──────────────────────────────────────────────────────────────────

def installed() {
    log.info "Zone Temperature Sync parent app installed"
}

def updated() {
    log.info "Zone Temperature Sync parent app updated"
}
