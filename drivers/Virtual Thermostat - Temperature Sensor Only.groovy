metadata {
    definition(name: "Virtual Thermostat - Temperature Sensor Only", namespace: "jamesfrank", author: "James Frank") {
        capability "Thermostat"
        capability "TemperatureMeasurement"
        capability "Sensor"
        capability "Actuator"

        attribute "temperature", "number"
        attribute "thermostatMode", "string"
        attribute "supportedThermostatModes", "enum"
        attribute "thermostatOperatingState", "string"

        command "setTemperature", [[name:"Temperature*", type: "NUMBER", description: "Set current temperature"]]
        command "setHeatingSetpoint", [[name:"Heating Setpoint*", type: "NUMBER", description: "Set heating setpoint (mirrors temperature)"]]
        command "setCoolingSetpoint", [[name:"Cooling Setpoint*", type: "NUMBER", description: "Set cooling setpoint (mirrors temperature)"]]
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
    // Only "off" supported
    if (mode == "off") {
        sendEvent(name: "thermostatMode", value: "off")
    } else {
        log.warn "Unsupported thermostat mode '${mode}' ignored. Only 'off' is supported."
    }
}

def logsOff() {
    log.warn "Debug logging disabled"
    device.updateSetting("debugLogging", [value: "false", type: "bool"])
}
