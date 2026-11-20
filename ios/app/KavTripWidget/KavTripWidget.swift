import WidgetKit
import SwiftUI
import ActivityKit

@available(iOS 16.1, *)
@main
struct KavTripActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: KavTripAttributes.self) { context in
            // Lock Screen / Notification Center banner
            KavTripBannerView(state: context.state)
                .activityBackgroundTint(Color.black.opacity(0.85))
        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded Leading: Transit mode icon + prominent route pill badge
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 5) {
                        Image(systemName: iconForMode(context.state.modeName))
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color.accentOrWhite(hex: context.state.tintColorHex))

                        Text(context.state.chip.isEmpty ? "Trip" : context.state.chip)
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 9)
                    .padding(.vertical, 4)
                    .background(Color.accentOrWhite(hex: context.state.tintColorHex).opacity(0.25))
                    .overlay(
                        Capsule().stroke(Color.accentOrWhite(hex: context.state.tintColorHex).opacity(0.4), lineWidth: 1)
                    )
                    .clipShape(Capsule())
                }

                // Expanded Trailing: Estimated arrival time & departure countdown
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 2) {
                        HStack(spacing: 4) {
                            Image(systemName: "flag.checkered")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(.secondary)
                            Text(context.state.arrivalTime.isEmpty ? "--:--" : context.state.arrivalTime)
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .monospacedDigit()
                                .foregroundColor(.white)
                        }
                        if !context.state.dueText.isEmpty {
                            Text(context.state.dueText)
                                .font(.system(size: 12, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.accentOrWhite(hex: context.state.tintColorHex))
                        }
                    }
                }

                // Expanded Center
                DynamicIslandExpandedRegion(.center) {
                    EmptyView()
                }

                // Expanded Bottom: Step title, status, and clean progress indicator
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 5) {
                        Text(context.state.title.isEmpty ? "Trip in progress" : context.state.title)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                            .lineLimit(1)

                        if !context.state.subtitle.isEmpty {
                            Text(context.state.subtitle)
                                .font(.system(size: 12, weight: .regular))
                                .foregroundColor(Color(white: 0.8))
                                .lineLimit(1)
                        }

                        ProgressView(value: safeProgress(context.state.progress))
                            .tint(Color.accentOrWhite(hex: context.state.tintColorHex))
                            .padding(.top, 2)
                    }
                    .padding(.top, 4)
                }
            } compactLeading: {
                // Compact Leading: Transit icon + Route chip badge
                HStack(spacing: 3) {
                    Image(systemName: iconForMode(context.state.modeName))
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(Color.accentOrWhite(hex: context.state.tintColorHex))
                    Text(context.state.chip.isEmpty ? "Trip" : context.state.chip)
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
            } compactTrailing: {
                // Compact Trailing: Departure countdown if waiting, otherwise destination ETA
                let label = compactTrailingText(dueText: context.state.dueText, arrivalTime: context.state.arrivalTime)
                Text(label)
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .foregroundColor(context.state.dueText.isEmpty ? .white : Color.accentOrWhite(hex: context.state.tintColorHex))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            } minimal: {
                // Minimal: Transit icon in route tint
                Image(systemName: iconForMode(context.state.modeName))
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(Color.accentOrWhite(hex: context.state.tintColorHex))
            }
            .widgetURL(URL(string: "moovit://trip"))
            .keylineTint(Color.accentOrWhite(hex: context.state.tintColorHex))
        }
    }
}

// Lock Screen / Notification Center Card
@available(iOS 16.1, *)
struct KavTripBannerView: View {
    let state: KavTripAttributes.ContentState

    var body: some View {
        VStack(spacing: 9) {
            HStack(alignment: .center) {
                // Transit icon + Line Badge
                HStack(spacing: 6) {
                    Image(systemName: iconForMode(state.modeName))
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(Color.accentOrWhite(hex: state.tintColorHex))
                    Text(state.chip.isEmpty ? "Trip" : state.chip)
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .padding(.horizontal, 9)
                        .padding(.vertical, 3)
                        .background(Color.accentOrWhite(hex: state.tintColorHex).opacity(0.25))
                        .overlay(
                            Capsule().stroke(Color.accentOrWhite(hex: state.tintColorHex).opacity(0.4), lineWidth: 1)
                        )
                        .foregroundColor(.white)
                        .clipShape(Capsule())
                }

                Spacer()

                // Final Arrival Time & Due Countdown
                VStack(alignment: .trailing, spacing: 2) {
                    HStack(spacing: 4) {
                        Image(systemName: "flag.checkered")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(.secondary)
                        Text(state.arrivalTime.isEmpty ? "--:--" : state.arrivalTime)
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .foregroundColor(.white)
                    }
                    if !state.dueText.isEmpty {
                        Text(state.dueText)
                            .font(.system(size: 11, weight: .semibold, design: .rounded))
                            .foregroundColor(Color.accentOrWhite(hex: state.tintColorHex))
                    }
                }
            }

            // Step Details
            VStack(alignment: .leading, spacing: 2) {
                Text(state.title.isEmpty ? "Trip in progress" : state.title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                if !state.subtitle.isEmpty {
                    Text(state.subtitle)
                        .font(.system(size: 12))
                        .foregroundColor(Color(white: 0.8))
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // Progress Bar
            ProgressView(value: safeProgress(state.progress))
                .tint(Color.accentOrWhite(hex: state.tintColorHex))
        }
        .padding(14)
    }
}

private func compactTrailingText(dueText: String, arrivalTime: String) -> String {
    if !dueText.isEmpty {
        let trimmed = dueText.replacingOccurrences(of: "^(in |בעוד )", with: "", options: .regularExpression)
        return trimmed.isEmpty ? dueText : trimmed
    }
    return arrivalTime.isEmpty ? "--:--" : arrivalTime
}

private func safeProgress(_ value: Double) -> Double {
    if value.isNaN || value.isInfinite { return 0.0 }
    return max(0.0, min(1.0, value))
}

private func iconForMode(_ mode: String) -> String {
    switch mode.lowercased() {
    case "bus": return "bus.fill"
    case "train": return "tram.tunnel.fill"
    case "subway": return "tram.fill"
    case "tram": return "tram"
    case "ferry": return "ferry.fill"
    case "cable", "gondola", "funicular": return "cablecar.fill"
    default: return "figure.walk"
    }
}

extension Color {
    init(hex: String) {
        let clean = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        let int = UInt64(clean, radix: 16) ?? 0
        let r, g, b: UInt64
        switch clean.count {
        case 6:
            (r, g, b) = ((int >> 16) & 0xFF, (int >> 8) & 0xFF, int & 0xFF)
        default:
            (r, g, b) = (90, 160, 255)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255.0,
            green: Double(g) / 255.0,
            blue: Double(b) / 255.0,
            opacity: 1.0
        )
    }

    static func accentOrWhite(hex: String) -> Color {
        let clean = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        guard clean.count == 6, let int = UInt64(clean, radix: 16) else {
            return Color(red: 0.38, green: 0.65, blue: 1.0)
        }
        let r = Double((int >> 16) & 0xFF) / 255.0
        let g = Double((int >> 8) & 0xFF) / 255.0
        let b = Double(int & 0xFF) / 255.0
        let luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        if luminance < 0.25 {
            return Color(red: 0.38, green: 0.65, blue: 1.0)
        }
        return Color(.sRGB, red: r, green: g, blue: b, opacity: 1.0)
    }
}
