import SwiftUI

/// The iOS app's entry point. Everything on screen is the shared Compose Multiplatform UI.
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            // The shared UI handles the notch and home indicator itself (safeDrawingPadding),
            // so the SwiftUI host lets it draw edge to edge.
            ContentView().ignoresSafeArea()
        }
    }
}
