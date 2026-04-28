metadata {
    definition(name: "Virtual Shade with Battery", namespace: "jamesfrank", author: "James Frank") {
        capability "Actuator"
        capability "Sensor"

        capability "WindowShade"
        capability "SwitchLevel"
        capability "Switch"        // required for HomeKit battery exposure
        capability "Battery"

        attribute "position", "number"  // <-- explicit position attribute

        command "pause"
        command "setPosition", [[name:"Position*", type:"NUMBER"]]
        command "setBattery", [[name:"Battery Level*", type:"NUMBER"]]
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

/****************************************
 * Lifecycle
 ****************************************/

def installed() {
    initialize()
}

def updated() {
    initialize()
}

def initialize() {
    if (device.currentValue("position") == null) {
        sendEvent(name: "position", value: 0)
    }
    if (device.currentValue("level") == null) {
        sendEvent(name: "level", value: 0)
    }
    if (device.currentValue("windowShade") == null) {
        sendEvent(name: "windowShade", value: "closed")
    }
    if (device.currentValue("switch") == null) {
        sendEvent(name: "switch", value: "off")
    }
    if (device.currentValue("battery") == null) {
        sendEvent(name: "battery", value: 100, unit: "%")
    }
}

/****************************************
 * Shade Commands
 ****************************************/

def open() {
    logDebug "open()"
    setPosition(100)
}

def close() {
    logDebug "close()"
    setPosition(0)
}

def pause() {
    logDebug "pause()"
    sendEvent(name: "windowShade", value: "partially open")
}

/****************************************
 * Core Sync Logic (KEY PART)
 ****************************************/

private updateShadeState(pos) {
    def shadeState
    if (pos == 0) {
        shadeState = "closed"
        sendEvent(name: "switch", value: "off", displayed: false)
    } else if (pos == 100) {
        shadeState = "open"
        sendEvent(name: "switch", value: "on", displayed: false)
    } else {
        shadeState = "partially open"
        sendEvent(name: "switch", value: "on", displayed: false)
    }

    sendEvent(name: "windowShade", value: shadeState)
}

/****************************************
 * Position + Level Handling
 ****************************************/

def setPosition(pos) {
    pos = Math.max(Math.min(pos as Integer, 100), 0)
    logDebug "setPosition(${pos})"

    // Update BOTH attributes (this is what the built-in driver does)
    sendEvent(name: "position", value: pos, unit: "%")
    sendEvent(name: "level", value: pos, unit: "%")

    updateShadeState(pos)
}

// SwitchLevel capability uses setLevel()
def setLevel(level, rate = null) {
    level = Math.max(Math.min(level as Integer, 100), 0)
    logDebug "setLevel(${level})"

    // Delegate to same logic to keep them perfectly in sync
    setPosition(level)
}

/****************************************
 * Battery Handling
 ****************************************/

def setBattery(level) {
    level = Math.max(Math.min(level as Integer, 100), 0)
    logDebug "setBattery(${level})"
    sendEvent(name: "battery", value: level, unit: "%")
}

/****************************************
 * Logging
 ****************************************/

private logDebug(msg) {
    if (logEnable) log.debug msg
}
