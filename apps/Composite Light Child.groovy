/**
 * Light Merger — Child App
 *
 * Merges N virtual light sources (intent signals) into a single physical target.
 *
 * Behavior:
 *   • Power  : ON if ≥1 source is on; OFF if all sources are off
 *   • Level  : arithmetic mean of active-source levels (missing level → 100)
 *   • CT     : brightness-weighted mean of active sources that carry CT;
 *               omitted entirely if no active source has a CT attribute
 *
 * Architecture:
 *   initialize() → subscribe → handler() → debounced sync() → apply()
 */

definition(
    name        : "Composite Light Child",
    namespace   : "jamesfrank",
    author      : "James Frank",
    description : "Merges N virtual lights into one physical target",
    category    : "Convenience",
    parent      : "jamesfrank:Composite Light Manager",   // adjust to match your parent app
    iconUrl     : "",
    iconX2Url   : ""
)

// ──────────────────────────────────────────────
// Preferences / UI
// ──────────────────────────────────────────────
preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Composite Light Manager — Child", install: true, uninstall: true) {

        section("Instance name") {
            label title: "Name this instance", required: false
        }

        section("Source devices (virtual lights)") {
            input name: "sources",
                  type: "capability.switchLevel",
                  title: "Select source devices",
                  multiple: true,
                  required: true
        }

        section("Target device (physical light)") {
            input name: "target",
                  type: "capability.switchLevel",
                  title: "Select target device",
                  multiple: false,
                  required: true
        }

        section("Options") {
            input name: "debugEnabled",
                  type: "bool",
                  title: "Enable debug logging",
                  defaultValue: false
        }
    }
}

// ──────────────────────────────────────────────
// Lifecycle
// ──────────────────────────────────────────────
def installed() {
    logDebug "installed()"
    initialize()
}

def updated() {
    logDebug "updated()"
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    logDebug "initialize() — subscribing to ${sources?.size() ?: 0} source(s)"

    if (!sources) {
        log.warn "Light Merger [${app.label}]: no source devices configured"
        return
    }

    subscribe(sources, "switch",           handler)
    subscribe(sources, "level",            handler)
    subscribe(sources, "colorTemperature", handler)

    // Sync once at startup so the target reflects current source state immediately
    sync()
}

// ──────────────────────────────────────────────
// Event handler → debounce → sync
// ──────────────────────────────────────────────
def handler(evt) {
    logDebug "handler() — event: ${evt.displayName} [${evt.name}] = ${evt.value}"
    // Debounce: cancel any pending sync and reschedule 100 ms from now.
    // runInMillis is available on Hubitat 2.2.6+
    runInMillis(100, "sync")
}

// ──────────────────────────────────────────────
// Core recompute
// ──────────────────────────────────────────────
def sync() {
    logDebug "sync() — evaluating ${sources?.size() ?: 0} source(s)"

    if (!sources) {
        logDebug "sync() — no sources; turning target off"
        safeOff()
        return
    }

    // ── Collect active sources ──────────────────
    List activeSources = sources.findAll { dev ->
        def sw = dev.currentValue("switch")
        sw == "on"
    }

    logDebug "sync() — active sources: ${activeSources.collect { it.displayName }}"

    if (activeSources.isEmpty()) {
        logDebug "sync() — no active sources; turning target off"
        safeOff()
        return
    }

    // ── Level: arithmetic mean of active levels ─
    // Missing or null level → treat as 100
    def levels = activeSources.collect { dev ->
        def lvl = dev.currentValue("level")
        (lvl != null) ? (lvl as BigDecimal) : 100G
    }

    BigDecimal meanLevel = levels.sum() / levels.size()
    int targetLevel = meanLevel.setScale(0, BigDecimal.ROUND_HALF_UP).intValue()
    targetLevel = clamp(targetLevel, 1, 100)

    logDebug "sync() — levels: ${levels} → mean: ${targetLevel}"

    // ── CT: brightness-weighted mean (only if any active source has CT) ─
    Integer targetCT = null

    List ctSources = activeSources.findAll { dev ->
        def ct = dev.currentValue("colorTemperature")
        ct != null
    }

    if (ctSources) {
        BigDecimal weightedSum = 0G
        BigDecimal weightTotal = 0G

        ctSources.each { dev ->
            def ct  = dev.currentValue("colorTemperature") as BigDecimal
            def lvl = dev.currentValue("level")
            BigDecimal weight = (lvl != null) ? (lvl as BigDecimal) : 100G

            weightedSum  += (weight * ct)
            weightTotal  += weight
        }

        if (weightTotal > 0) {
            BigDecimal weightedCT = weightedSum / weightTotal
            targetCT = weightedCT.setScale(0, BigDecimal.ROUND_HALF_UP).intValue()
            logDebug "sync() — CT sources: ${ctSources.collect { it.displayName }} → weighted CT: ${targetCT}"
        }
    } else {
        logDebug "sync() — no active sources with CT; CT will not be sent to target"
    }

    // ── Apply ───────────────────────────────────
    apply(targetLevel, targetCT)
}

// ──────────────────────────────────────────────
// Apply computed state to target (safely)
// ──────────────────────────────────────────────
def apply(int level, Integer ct) {
    logDebug "apply(level=${level}, ct=${ct})"

    if (!target) {
        log.warn "Composite Light Child [${app.label}]: no target device configured"
        return
    }

    // Rule: ON must be sent before level / CT
    target.on()
    target.setLevel(level)

    if (ct != null) {
        if (target.hasCommand("setColorTemperature")) {
            logDebug "apply() — sending setColorTemperature(${ct})"
            target.setColorTemperature(ct)
        } else {
            logDebug "apply() — target lacks setColorTemperature command; skipping CT"
        }
    }
}

// ──────────────────────────────────────────────
// Safe off
// ──────────────────────────────────────────────
private void safeOff() {
    if (target) {
        logDebug "safeOff() — turning target off"
        target.off()
    }
}

// ──────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────
private int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value))
}

private void logDebug(String msg) {
    if (debugEnabled) log.debug "Composite Light Child [${app.label}]: ${msg}"
}
