metadata {
    definition(name: "Virtual Thermostat - Temperature Sensor Only - with On Off Modes", namespace: "jamesfrank", author: "James Frank") {
        capability "Thermostat"
        capability "TemperatureMeasurement"
        capability "Sensor"
        capability "Actuator"
        capability "Switch"

        // Fan related
        attribute "fanMode", "string"
        attribute "supportedFanModes", "enum"
        attribute "thermostatFanMode", "string"
        attribute "supportedThermostatFanModes", "enum"

        attribute "temperature", "number"
        attribute "thermostatMode", "string"
        attribute "supportedThermostatModes", "enum"
        attribute "thermostatOperatingState", "string"

        command "setTemperature", [[name:"Temperature*", type: "NUMBER", description: "Set current temperature"]]
        command "setHeatingSetpoint", [[name:"Heating Setpoint*", type: "NUMBER", description: "Set heating setpoint (mirrors temperature)"]]
        command "setCoolingSetpoint", [[name:"Cooling Setpoint*", type: "NUMBER", description: "Set cooling setpoint (mirrors temperature)"]]
        command "setFanMode", [[name:"Fan Mode*", type:"ENUM", constraints:["auto","on"], description:"Set fan mode"]]
    }

    preferences {
        input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "tempUnit", type: "enum", title: "Temperature Unit", options: ["°F", "°C"], defaultValue: "°F"
    }
}

def installed() {
    log.debug "Installed"
    initialize()
}

def updated() {
    log.debug "Updated"
    if (debugLogging) runIn(1800, "logsOff")
    initialize()
}

def initialize() {
    sendEvent(name: "temperature", value: 72, unit: tempUnit ?: "°F")
    sendEvent(name: "thermostatMode", value: "off")
    sendEvent(name: "supportedThermostatModes", value: ["off"])
    sendEvent(name: "thermostatOperatingState", value: "idle")

    // Fan states
    def supported = ["auto","on"]
    sendEvent(name: "supportedFanModes", value: supported)
    sendEvent(name: "supportedThermostatFanModes", value: supported)
    setFanMode("auto") // default (shows as off in HomeKit via Switch)
}

def setTemperature(temp) {
    updateTemperature(temp)
}

def setHeatingSetpoint(temp) {
    if (debugLogging) log.debug "Setting heating setpoint to ${temp}${tempUnit}"
    updateTemperature(temp)
}

def setCoolingSetpoint(temp) {
    if (debugLogging) log.debug "Setting cooling setpoint to ${temp}${tempUnit}"
    updateTemperature(temp)
}

private updateTemperature(temp) {
    if (debugLogging) log.debug "Updating temperature to ${temp}${tempUnit}"
    sendEvent(name: "temperature", value: temp, unit: tempUnit ?: "°F")
}

def setThermostatMode(mode) {
    if (mode == "off") {
        sendEvent(name: "thermostatMode", value: "off")
    } else {
        log.warn "Unsupported thermostat mode '${mode}' ignored. Only 'off' is supported."
    }
}

def setFanMode(mode) {
    def supported = ["auto","on"]
    if (supported.contains(mode)) {
        if (debugLogging) log.debug "Setting fan mode to ${mode}"
        sendEvent(name: "fanMode", value: mode)
        sendEvent(name: "thermostatFanMode", value: mode)

        // Keep Switch capability in sync
        def swValue = (mode == "on") ? "on" : "off"
        sendEvent(name: "switch", value: swValue)
    } else {
        log.warn "Unsupported fan mode '${mode}' ignored. Supported modes: ${supported}"
    }
}

// Standard Switch commands (so WebCoRE, Rule Machine, Dashboard, HomeKit can call Turn On/Off)
def on() {
    if (debugLogging) log.debug "Turning ON (fan = on)"
    setFanMode("on")
}

def off() {
    if (debugLogging) log.debug "Turning OFF (fan = auto)"
    setFanMode("auto")
}

def logsOff() {
    log.warn "Debug logging disabled"
    device.updateSetting("debugLogging", [value: "false", type: "bool"])
}
