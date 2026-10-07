import SwiftUI
import KavKit

@main
struct KavApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea(.all).background(Color.black.ignoresSafeArea())
        }
    }
}

// Hosts the Compose Multiplatform UI built in ios/kav (see MainViewController.kt).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
