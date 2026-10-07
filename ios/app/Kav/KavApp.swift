import SwiftUI
import KavKit

// KavKit exports every public Kotlin type under its bare name (`Context`, `Color`, `View`, ...), so any SwiftUI name
// that the Kotlin side also uses has to be written out in full here.

@main
struct KavApp: SwiftUI.App {
    var body: some SwiftUI.Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea(.all).background(SwiftUI.Color.black.ignoresSafeArea())
        }
    }
}

// Hosts the Compose Multiplatform UI built in ios/kav (see MainViewController.kt).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}
