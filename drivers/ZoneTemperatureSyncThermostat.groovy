/**
 * Zone Temperature Sync - Virtual Thermostat Driver
 *
 * A virtual thermostat device used as the target for the Zone Temperature Sync app.
 * Exposes Temperature, Humidity, Battery, and Thermostat capabilities so that
 * HomeKit (via Hubitat's native HomeKit integration) shows:
 *   - Current zone temperature and humidity
 *   - Thermostat mode: "heat" = valid data incoming, "off" = no valid sensor data
 *   - Battery level: reflects minimum battery of all linked sensors,
 *                    or 0% if any sensor is offline (triggers HomeKit low-battery alert)
 *
 * Install this driver first, then create a Virtual Device using it for each zone.
 */

metadata {
    definition(
        name:        "Zone Temperature Sync Thermostat",
        namespace:   "jamesfrank",
        author:      "Zone Temperature Sync",
        description: "Virtual thermostat target for Zone Temperature Sync zone apps"
    ) {
        capability "TemperatureMeasurement"          // attribute: temperature
        capability "RelativeHumidityMeasurement"     // attribute: humidity
        capability "Battery"                         // attribute: battery
        capability "Thermostat"                      // attributes: thermostatMode, thermostatOperatingState,
                                                     //             heatingSetpoint, coolingSetpoint, thermostatFanMode
        capability "Refresh"

        // Commands the Zone Temperature Sync child app will call
        command "setTemperature",    [[name: "temperature*", type: "NUMBER", description: "Temperature value"]]
        command "setHumidity",       [[name: "humidity*",    type: "NUMBER", description: "Humidity % (0–100)"]]
        command "setBattery",        [[name: "battery*",     type: "NUMBER", description: "Battery % (0–100)"]]

        // Additional state info for diagnostics (not used by HomeKit, but visible in Hubitat)
        attribute "validSensorCount",   "number"
        attribute "offlineSensorCount", "number"
        attribute "lastSyncTime",       "string"
    }

    preferences {
        input "tempUnit",   "enum",   title: "Temperature Unit",        options: ["F", "C"], defaultValue: "F"
        input "logEnable",  "bool",   title: "Enable debug logging",    defaultValue: false
        input "descLog",    "bool",   title: "Enable description logging", defaultValue: true
    }
}

// ──────────────────────────────────────────────────────────────────
// Lifecycle
// ──────────────────────────────────────────────────────────────────

def installed() {
    log.info "${device.displayName}: installed"
    initialize()
}

def updated() {
    log.info "${device.displayName}: updated"
    initialize()
}

def initialize() {
    // Set safe defaults so HomeKit sees a coherent device from the start
    if (device.currentValue("thermostatMode")          == null) sendEvent(name: "thermostatMode",          value: "off")
    if (device.currentValue("thermostatOperatingState") == null) sendEvent(name: "thermostatOperatingState", value: "idle")
    if (device.currentValue("thermostatFanMode")        == null) sendEvent(name: "thermostatFanMode",        value: "auto")
    if (device.currentValue("heatingSetpoint")          == null) sendEvent(name: "heatingSetpoint",          value: 70,  unit: "°${tempUnit ?: 'F'}")
    if (device.currentValue("coolingSetpoint")          == null) sendEvent(name: "coolingSetpoint",          value: 75,  unit: "°${tempUnit ?: 'F'}")
    if (device.currentValue("battery")                  == null) sendEvent(name: "battery",                  value: 100, unit: "%")
    if (device.currentValue("validSensorCount")         == null) sendEvent(name: "validSensorCount",         value: 0)
    if (device.currentValue("offlineSensorCount")       == null) sendEvent(name: "offlineSensorCount",       value: 0)

    sendEvent(name: "supportedThermostatModes",    value: ["off", "heat"])
    sendEvent(name: "supportedThermostatFanModes", value: ["auto"])
}

def refresh() {
    logDebug "refresh() called - no-op for virtual device"
}

// ──────────────────────────────────────────────────────────────────
// Commands called by the Zone Temperature Sync app
// ──────────────────────────────────────────────────────────────────

def setTemperature(value) {
    def unit = "°${tempUnit ?: 'F'}"
    def rounded = value != null ? (value as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP) : null
    logDebug "setTemperature: ${rounded} ${unit}"
    if (rounded != null) {
        sendEvent(name: "temperature", value: rounded, unit: unit)
        if (descLog) log.info "${device.displayName}: temperature → ${rounded} ${unit}"
    }
}

def setHumidity(value) {
    def rounded = value != null ? (value as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP) : null
    logDebug "setHumidity: ${rounded} %"
    if (rounded != null) {
        sendEvent(name: "humidity", value: rounded, unit: "%")
        if (descLog) log.info "${device.displayName}: humidity → ${rounded}%"
    }
}

def setBattery(value) {
    def level = value != null ? (value as Integer) : null
    logDebug "setBattery: ${level} %"
    if (level != null) {
        sendEvent(name: "battery", value: level, unit: "%")
        if (descLog) log.info "${device.displayName}: battery → ${level}%"
    }
}

// Called by the app to indicate data validity; "heat" = valid data, "off" = no valid data
def setThermostatMode(mode) {
    logDebug "setThermostatMode: ${mode}"
    sendEvent(name: "thermostatMode", value: mode)
    if (descLog) log.info "${device.displayName}: thermostatMode → ${mode}"
}

// ──────────────────────────────────────────────────────────────────
// Standard Thermostat capability commands (required by capability;
// mostly no-ops for a virtual read-only zone display device)
// ──────────────────────────────────────────────────────────────────

def off()           { setThermostatMode("off")            }
def heat()          { setThermostatMode("heat")           }
def cool()          { setThermostatMode("cool")           }
def auto()          { setThermostatMode("auto")           }
def emergencyHeat() { setThermostatMode("emergency heat") }

def fanAuto()       { setThermostatFanMode("auto")        }
def fanOn()         { setThermostatFanMode("on")          }
def fanCirculate()  { setThermostatFanMode("circulate")   }

def setThermostatFanMode(fanMode) {
    sendEvent(name: "thermostatFanMode", value: fanMode)
}

def setHeatingSetpoint(temp) {
    sendEvent(name: "heatingSetpoint", value: temp, unit: "°${tempUnit ?: 'F'}")
}

def setCoolingSetpoint(temp) {
    sendEvent(name: "coolingSetpoint", value: temp, unit: "°${tempUnit ?: 'F'}")
}

// ──────────────────────────────────────────────────────────────────
// Internal helpers
// ──────────────────────────────────────────────────────────────────

private logDebug(msg) {
    if (logEnable) log.debug "${device.displayName}: ${msg}"
}
