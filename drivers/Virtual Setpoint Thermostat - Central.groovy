metadata {
    definition(name: "Virtual Setpoint Thermostat - Central", namespace: "jamesfrank", author: "James Frank") {
        capability "Thermostat"
        capability "Sensor"
        capability "Actuator"

        attribute "heatingSetpoint", "number"
        attribute "coolingSetpoint", "number"
        attribute "thermostatMode", "string"
        attribute "supportedThermostatModes", "enum"
        attribute "thermostatFanMode", "string"
        attribute "supportedThermostatFanModes", "enum"

        command "setHeatingSetpoint", [[name:"Heating Setpoint*", type: "NUMBER", description: "Set heating temperature"]]
        command "setCoolingSetpoint", [[name:"Cooling Setpoint*", type: "NUMBER", description: "Set cooling temperature"]]
        command "setThermostatMode", [[name:"Thermostat Mode*", type: "ENUM", constraints: ["off", "cool", "heat", "auto"]]]
        command "setThermostatFanMode", [[name:"Fan Mode*", type: "ENUM", constraints: ["auto", "circulate"]]]
    }

    preferences {
        input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: true
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
    sendEvent(name: "heatingSetpoint", value: 68, unit: "°F")
    sendEvent(name: "coolingSetpoint", value: 75, unit: "°F")
    sendEvent(name: "thermostatMode", value: "off")
    sendEvent(name: "supportedThermostatModes", value: ["off", "cool", "heat", "auto"])
    sendEvent(name: "thermostatFanMode", value: "auto")
    sendEvent(name: "supportedThermostatFanModes", value: ["auto", "circulate"])
}

def setHeatingSetpoint(temp) {
    if (debugLogging) log.debug "Setting heating setpoint to ${temp}"
    sendEvent(name: "heatingSetpoint", value: temp, unit: "°F")
}

def setCoolingSetpoint(temp) {
    if (debugLogging) log.debug "Setting cooling setpoint to ${temp}"
    sendEvent(name: "coolingSetpoint", value: temp, unit: "°F")
}

def setThermostatMode(mode) {
    def allowedModes = ["off", "cool", "heat", "auto"]
    if (allowedModes.contains(mode)) {
        if (debugLogging) log.debug "Setting thermostat mode to ${mode}"
        sendEvent(name: "thermostatMode", value: mode)
    } else {
        log.warn "Unsupported thermostat mode '${mode}' ignored. Allowed modes: ${allowedModes.join(', ')}"
    }
}

def setThermostatFanMode(mode) {
    def allowedFanModes = ["auto", "circulate"]
    if (allowedFanModes.contains(mode)) {
        if (debugLogging) log.debug "Setting fan mode to ${mode}"
        sendEvent(name: "thermostatFanMode", value: mode)
    } else {
        log.warn "Unsupported fan mode '${mode}' ignored. Allowed fan modes: ${allowedFanModes.join(', ')}"
    }
}

def logsOff() {
    log.warn "Debug logging disabled"
    device.updateSetting("debugLogging", [value: "false", type: "bool"])
}
