import ComposeApp
import SwiftUI
import UIKit

/// Puts the shared UI (Kotlin `MainViewController()` in composeApp/src/iosMain) inside SwiftUI.
struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // The Compose UI manages its own state; nothing to push from SwiftUI.
    }
}
