/**
 * Light Merger — Parent App
 *
 * Container app for Light Merger child instances.
 * Handles child app creation, listing, and removal.
 * Does not perform any device logic itself.
 */

definition(
    name        : "Composite Light Manager",
    namespace   : "jamesfrank",
    author      : "James Frank",
    description : "Manages Composite Light Manager child instances",
    category    : "Convenience",
    singleInstance: true,
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
    dynamicPage(name: "mainPage", title: "Composite Light Manager", install: true, uninstall: true) {

        section("Instances") {
            app(name        : "childApps",
                appName     : "Composite Light Child",
                namespace   : "jamesfrank",
                title       : "Add a new Composite Light Child instance",
                multiple    : true)
        }

        section("About") {
            paragraph "Each child instance merges N virtual light sources into one physical target. " +
                      "Tap above to create or configure instances."
        }
    }
}

// ──────────────────────────────────────────────
// Lifecycle — parent does no device work
// ──────────────────────────────────────────────
def installed() {
    log.info "Light Merger Manager: installed"
    initialize()
}

def updated() {
    log.info "Light Merger Manager: updated"
    initialize()
}

def initialize() {
    // Nothing to do at the parent level; all logic lives in children
}
