import SwiftUI
import KavKit

// KavKit (ios/kavkit) exports only the entry point now, but SwiftUI names stay qualified so a Kotlin type exported
// later can never shadow them.

@main
struct KavApp: SwiftUI.App {
    var body: some SwiftUI.Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea(.all).background(SwiftUI.Color.black.ignoresSafeArea())
        }
    }
}

// Hosts the Compose Multiplatform UI (ios/kavkit/src/iosMain/kotlin/MainViewController.kt).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}
