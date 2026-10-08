import SwiftUI
import KavKit

// KavKit exports every public Kotlin type under its bare name (`Context`, `Color`, `View`, ...), so any SwiftUI name
// that the Kotlin side also uses has to be written out in full here.

@main
struct KavApp: SwiftUI.App {
    var body: some SwiftUI.Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea(.all).background(SwiftUI.Color.black.ignoresSafeArea())
                // TEMPORARY black-screen bisect: drawn by SwiftUI above Compose.
                .overlay(alignment: .bottom) {
                    SwiftUI.TimelineView(.periodic(from: .now, by: 1)) { t in
                        SwiftUI.Text("SwiftUI layer \(Int(t.date.timeIntervalSince1970) % 100)")
                            .font(.headline).foregroundColor(.red).padding(.bottom, 60)
                    }
                }
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
