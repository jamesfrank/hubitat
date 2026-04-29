/**
 * Zone Temperature Sync Child (Child App)
 *
 * One instance of this child app manages one zone:
 *   - Monitors N temperature sensors and M humidity sensors
 *   - Determines which sensors are online and reporting valid data
 *   - Averages readings from valid sensors
 *   - Pushes composite temperature, humidity, and battery values
 *     to a virtual "Zone Temperature Sync Thermostat" target device
 *
 * Sensor health is evaluated on two axes:
 *   1. healthStatus attribute - if present and "offline", sensor is excluded
 *   2. Activity timeout     - if lastActivity is older than the configured
 *                             window, sensor is excluded
 *
 * Updates are event-driven (subscribe to attribute changes) plus a scheduled
 * periodic sweep to catch sensors that have silently stopped reporting.
 */

definition(
    name:        "Zone Temperature Sync Child",
    namespace:   "jamesfrank",
    author:      "Zone Temperature Sync",
    description: "Syncs temperature/humidity sensors to a virtual thermostat zone.",
    category:    "Convenience",
    parent:      "jamesfrank:Zone Temperature Sync",
    iconUrl:     "",
    iconX2Url:   ""
)

preferences {
    page(name: "mainPage")
    page(name: "sensorStatusPage")
}

// ──────────────────────────────────────────────────────────────────
// Preferences pages
// ──────────────────────────────────────────────────────────────────

def mainPage() {
    dynamicPage(name: "mainPage", title: "Zone Configuration", install: true, uninstall: true) {

        section("<h2>Zone Identity</h2>") {
            label title: "Zone Name (also sets the app label)", required: true
        }

        section("<h2>Target Virtual Thermostat</h2>") {
            input "targetThermostat", "capability.thermostat",
                  title:    "Virtual thermostat device for this zone",
                  description: "Select a device using the 'Zone Temperature Sync Thermostat' driver",
                  required: true,
                  multiple: false
        }

        section("<h2>Temperature Sensors</h2>") {
            input "temperatureSensors", "capability.temperatureMeasurement",
                  title:       "Temperature sensor devices",
                  description: "All valid (online) sensors will be averaged",
                  required:    false,
                  multiple:    true,
                  submitOnChange: true
        }

        section("<h2>Humidity Sensors</h2>") {
            paragraph "Tip: If your temperature sensors also report humidity, add them here as well."
            input "humiditySensors", "capability.relativeHumidityMeasurement",
                  title:       "Humidity sensor devices",
                  description: "All valid (online) sensors will be averaged",
                  required:    false,
                  multiple:    true,
                  submitOnChange: true
        }

        section("<h2>Sensor Health</h2>") {
            paragraph(
                "A sensor is considered <b>offline</b> if either of the following is true:<br>" +
                "• Its <code>healthStatus</code> attribute reports 'offline' (Z-Wave/Zigbee devices with health monitoring)<br>" +
                "• It has not sent any event to the hub within the timeout window below"
            )
            input "sensorTimeoutHours", "decimal",
                  title:        "Activity timeout (hours)",
                  description:  "Sensors with no hub activity in this window are excluded",
                  defaultValue: 24,
                  required:     true
            input "useHealthStatus", "bool",
                  title:        "Check healthStatus attribute (recommended for Z-Wave/Zigbee)",
                  description:  "Disable only if your devices erroneously report 'offline'",
                  defaultValue: true
        }

        section("<h2>Update Schedule</h2>") {
            paragraph(
                "Updates happen automatically on every sensor event. " +
                "The periodic sweep below catches sensors that have silently stopped reporting."
            )
            input "sweepIntervalMinutes", "enum",
                  title:        "Periodic health-sweep interval",
                  options:      ["15": "Every 15 minutes", "30": "Every 30 minutes",
                                 "60": "Every hour",       "120": "Every 2 hours"],
                  defaultValue: "60",
                  required:     true
        }

        section("<h2>Thermostat 'Online' Mode</h2>") {
            paragraph(
                "When at least one valid temperature sensor exists, the target thermostat's mode is set to indicate " +
                "data is valid. When no valid sensors exist, mode is set to 'off' - HomeKit displays the thermostat " +
                "as off, signalling the zone has no current data."
            )
            input "onlineMode", "enum",
                  title:        "Thermostat mode when zone has valid data",
                  options:      ["heat": "heat", "cool": "cool", "auto": "auto"],
                  defaultValue: "heat",
                  required:     true
        }

        section("<h2>Logging</h2>") {
            input "logEnable", "bool",  title: "Enable debug logging", defaultValue: false
            input "descLog",   "bool",  title: "Enable info-level event logging", defaultValue: true
        }

        if (targetThermostat) {
            section {
                href "sensorStatusPage", title: "📊 View Current Sensor Status", description: "Tap to see which sensors are currently considered online/offline"
            }
        }
    }
}

def sensorStatusPage() {
    dynamicPage(name: "sensorStatusPage", title: "Sensor Status - ${app.label ?: 'Zone'}", install: false, uninstall: false) {
        def allSensors = getAllSensors()
        if (!allSensors) {
            section { paragraph "No sensors configured yet." }
            return
        }

        section("<h2>Sensor Health Report</h2>") {
            allSensors.each { dev ->
                def online       = isDeviceOnline(dev)
                def healthStatus = dev.currentValue("healthStatus") ?: "n/a"
                def lastAct      = dev.lastActivity
                def lastActStr   = lastAct ? formatElapsed(now() - lastAct.time) + " ago" : "never"
                def battery      = dev.currentValue("battery")
                def battStr      = battery != null ? "${battery}%" : "n/a"
                def statusIcon   = online ? "✅" : "⚠️"
                def temp         = dev.currentValue("temperature")
                def hum          = dev.currentValue("humidity")
                def readingStr   = [temp != null ? "${temp}°" : null, hum != null ? "${hum}%" : null].findAll { it }.join(" / ") ?: "n/a"

                paragraph(
                    "<b>${statusIcon} ${dev.displayName}</b><br>" +
                    "Status: <b>${online ? 'Online' : 'OFFLINE'}</b> | " +
                    "healthStatus: ${healthStatus} | " +
                    "Last activity: ${lastActStr}<br>" +
                    "Reading: ${readingStr} | Battery: ${battStr}"
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────
// Lifecycle
// ──────────────────────────────────────────────────────────────────

def installed() {
    logDebug "Installed"
    initialize()
}

def updated() {
    logDebug "Updated - re-initializing"
    unsubscribe()
    unschedule()
    initialize()
}

def uninstalled() {
    logDebug "Uninstalled"
}

def initialize() {
    logDebug "Initializing zone: ${app.label}"

    // ── Subscribe to sensor attribute events ─────────────────────
    if (temperatureSensors) {
        subscribe(temperatureSensors, "temperature", temperatureHandler)
    }

    if (humiditySensors) {
        subscribe(humiditySensors, "humidity", humidityHandler)
    }

    // healthStatus subscriptions - deduplicate devices that appear in both lists
    if (useHealthStatus) {
        def tempIds = temperatureSensors?.collect { it.id } as Set ?: [] as Set
        def humIds  = humiditySensors?.collect  { it.id } as Set ?: [] as Set
        def allIds  = tempIds + humIds

        if (temperatureSensors) subscribe(temperatureSensors, "healthStatus", healthStatusHandler)
        // Only subscribe humidity-only devices separately to avoid double-subscribing
        def humOnly = humiditySensors?.findAll { !(it.id in tempIds) }
        if (humOnly) subscribe(humOnly, "healthStatus", healthStatusHandler)
    }

    // Battery events for all unique devices
    def allDevices = getAllSensors()
    if (allDevices) {
        subscribe(allDevices, "battery", batteryHandler)
    }

    // ── Schedule periodic health sweep ────────────────────────────
    def interval = (sweepIntervalMinutes ?: "60") as Integer
    switch (interval) {
        case 15:  runEvery15Minutes("periodicSweep"); break
        case 30:  runEvery30Minutes("periodicSweep"); break
        case 120: runEvery3Hours("periodicSweep");    break    // closest built-in to 2h
        default:  runEvery1Hour("periodicSweep");     break
    }

    // ── Defer initial full update by 3 seconds ───────────────────
    runIn(3, "updateZone")
}

// ──────────────────────────────────────────────────────────────────
// Event handlers
// ──────────────────────────────────────────────────────────────────

def temperatureHandler(evt) {
    logDebug "temperature event ← ${evt.displayName}: ${evt.value}"
    updateTemperatureAndHumidity()
    // Battery doesn't change on a temp event - skip updateBattery() here for efficiency
}

def humidityHandler(evt) {
    logDebug "humidity event ← ${evt.displayName}: ${evt.value}"
    updateTemperatureAndHumidity()
}

def batteryHandler(evt) {
    logDebug "battery event ← ${evt.displayName}: ${evt.value}"
    // Battery changes are independent of temp/humidity; only update battery
    updateBattery()
}

def healthStatusHandler(evt) {
    logDebug "healthStatus event ← ${evt.displayName}: ${evt.value}"
    // A device just came online or went offline; full update needed
    updateZone()
}

def periodicSweep() {
    logDebug "Periodic health sweep running"
    updateZone()
}

// ──────────────────────────────────────────────────────────────────
// Core update logic
// ──────────────────────────────────────────────────────────────────

/**
 * Full zone update: temperature/humidity averages + battery minimum.
 * Called on health-status changes, periodic sweeps, or initial setup.
 */
def updateZone() {
    if (!targetThermostat) {
        log.warn "[${app.label}] No target thermostat configured - skipping update"
        return
    }
    updateTemperatureAndHumidity()
    updateBattery()
}

/**
 * Compute and push averaged temperature and humidity to the target thermostat.
 * Also updates thermostatMode to reflect data validity.
 */
def updateTemperatureAndHumidity() {
    if (!targetThermostat) return

    // ── Temperature ───────────────────────────────────────────────
    def validTempSensors = temperatureSensors ? temperatureSensors.findAll { isDeviceOnline(it) } : []

    if (validTempSensors.isEmpty()) {
        // No valid temperature data → signal "offline" via thermostat mode
        if (descLog) log.info "[${app.label}] No valid temperature sensors - setting mode to 'off'"
        targetThermostat.setThermostatMode("off")
        // Leave the last known temperature in place rather than writing 0 or null;
        // HomeKit users will see mode=off as the "no data" indicator.
        updateDiagnostics(0, countOfflineSensors(temperatureSensors))
    } else {
        def temps = validTempSensors
            .collect { safeDouble(it.currentValue("temperature")) }
            .findAll { it != null }

        if (temps) {
            def avg = roundHalf(temps.sum() / temps.size())
            logDebug "Temperature average: ${avg} from ${temps.size()} of ${temperatureSensors?.size()} sensors"
            if (descLog) log.info "[${app.label}] Temperature → ${avg} (avg of ${temps.size()} sensor(s))"
            targetThermostat.setTemperature(avg)
            targetThermostat.setThermostatMode(onlineMode ?: "heat")
        }
        updateDiagnostics(validTempSensors.size(), countOfflineSensors(temperatureSensors))
    }

    // ── Humidity ──────────────────────────────────────────────────
    def validHumSensors = humiditySensors ? humiditySensors.findAll { isDeviceOnline(it) } : []

    if (!validHumSensors.isEmpty()) {
        def hums = validHumSensors
            .collect { safeDouble(it.currentValue("humidity")) }
            .findAll { it != null }

        if (hums) {
            def avg = roundHalf(hums.sum() / hums.size())
            logDebug "Humidity average: ${avg} from ${hums.size()} of ${humiditySensors?.size()} sensors"
            if (descLog) log.info "[${app.label}] Humidity → ${avg} (avg of ${hums.size()} sensor(s))"
            targetThermostat.setHumidity(avg)
        }
    }
}

/**
 * Compute and push battery level to the target thermostat.
 *
 * Rules:
 *   - If ANY sensor (temp or humidity) is considered offline → battery = 0
 *     (forces HomeKit low-battery alert to surface the problem)
 *   - Otherwise → battery = minimum battery level across all sensors
 *   - Sensors with no battery attribute are ignored in the minimum calculation
 */
def updateBattery() {
    if (!targetThermostat) return

    def allSensors = getAllSensors()
    if (!allSensors) return

    def anyOffline = allSensors.any { !isDeviceOnline(it) }

    if (anyOffline) {
        def offlineNames = allSensors.findAll { !isDeviceOnline(it) }*.displayName.join(", ")
        if (descLog) log.info "[${app.label}] Battery → 0 (offline sensor(s): ${offlineNames})"
        targetThermostat.setBattery(0)
        return
    }

    def batteries = allSensors
        .collect { safeInteger(it.currentValue("battery")) }
        .findAll { it != null }

    if (batteries) {
        def minBat = batteries.min()
        logDebug "Battery minimum: ${minBat}% across ${batteries.size()} sensor(s)"
        if (descLog) log.info "[${app.label}] Battery → ${minBat}%"
        targetThermostat.setBattery(minBat)
    } else {
        logDebug "No battery levels available from any sensor - leaving thermostat battery unchanged"
    }
}

// ──────────────────────────────────────────────────────────────────
// Sensor health evaluation
// ──────────────────────────────────────────────────────────────────

/**
 * Returns true if the device is considered online and providing valid data.
 *
 * A device is considered OFFLINE if ANY of the following is true:
 *   1. useHealthStatus is enabled AND healthStatus attribute == "offline"
 *   2. lastActivity is null (device has never reported)
 *   3. lastActivity is older than sensorTimeoutHours
 */
boolean isDeviceOnline(device) {
    if (device == null) return false

    // Check 1: explicit healthStatus attribute
    if (useHealthStatus) {
        def healthStatus = device.currentValue("healthStatus")
        if (healthStatus != null && healthStatus.toLowerCase() == "offline") {
            logDebug "${device.displayName} OFFLINE (healthStatus='offline')"
            return false
        }
    }

    // Check 2 & 3: activity timeout
    def lastActivity = device.lastActivity
    if (lastActivity == null) {
        logDebug "${device.displayName} OFFLINE (lastActivity is null)"
        return false
    }

    def timeoutMs    = ((sensorTimeoutHours ?: 24) as Double) * 3_600_000L
    def elapsedMs    = now() - lastActivity.time
    if (elapsedMs > timeoutMs) {
        logDebug "${device.displayName} OFFLINE (last activity ${formatElapsed(elapsedMs)} ago, timeout ${sensorTimeoutHours}h)"
        return false
    }

    return true
}

// ──────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────

/** Returns a deduplicated list of all configured sensor devices. */
List getAllSensors() {
    def all = []
    if (temperatureSensors) all += temperatureSensors
    if (humiditySensors)    all += humiditySensors
    return all.unique { it.id }
}

/** Counts sensors in the given list that are currently offline. */
int countOfflineSensors(sensors) {
    if (!sensors) return 0
    return sensors.count { !isDeviceOnline(it) }
}

/** Pushes diagnostic attributes to the target thermostat. */
void updateDiagnostics(int validCount, int offlineCount) {
    try {
        targetThermostat.sendEvent(name: "validSensorCount",   value: validCount)
        targetThermostat.sendEvent(name: "offlineSensorCount", value: offlineCount)
        targetThermostat.sendEvent(name: "lastSyncTime",       value: new Date().format("yyyy-MM-dd HH:mm:ss"))
    } catch (e) {
        logDebug "Could not update diagnostics (driver may not support custom attributes): ${e.message}"
    }
}

/** Safely convert a value to Double; returns null on failure. */
Double safeDouble(val) {
    if (val == null) return null
    try { return (val as String).toDouble() }
    catch (e) { return null }
}

/** Safely convert a value to Integer; returns null on failure. */
Integer safeInteger(val) {
    if (val == null) return null
    try { return (val as String).toDouble().round(0) as Integer }
    catch (e) { return null }
}

/** Round a Double to 1 decimal place using HALF_UP. */
BigDecimal roundHalf(Double val) {
    return new BigDecimal(val).setScale(1, BigDecimal.ROUND_HALF_UP)
}

/** Format milliseconds as a human-readable elapsed time string. */
String formatElapsed(long ms) {
    def seconds = ms / 1000
    if (seconds < 60)         return "${seconds.toLong()}s"
    def minutes = seconds / 60
    if (minutes < 60)         return "${minutes.toLong()}m"
    def hours = minutes / 60
    if (hours < 24)           return "${hours.toLong()}h"
    return "${(hours / 24).toLong()}d ${(hours % 24).toLong()}h"
}

private logDebug(String msg) {
    if (logEnable) log.debug "[${app.label}] ${msg}"
}
