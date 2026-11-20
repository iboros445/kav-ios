import WidgetKit
import SwiftUI
import ActivityKit

@available(iOS 16.1, *)
@main
struct KavTripWidgetBundle: WidgetBundle {
    var body: some Widget {
        KavTripActivityWidget()
    }
}

@available(iOS 16.1, *)
struct KavTripActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: KavTripAttributes.self) { context in
            // Lock Screen / Notification Center banner
            KavTripBannerView(state: context.state)
                .activityBackgroundTint(Color.black.opacity(0.85))
        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded Leading: Transit mode icon + route chip
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 6) {
                        Image(systemName: iconForMode(context.state.modeName))
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(Color.accentOrWhite(hex: context.state.tintColorHex))

                        if !context.state.chip.isEmpty {
                            Text(context.state.chip)
                                .font(.system(size: 14, weight: .bold, design: .rounded))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.accentOrWhite(hex: context.state.tintColorHex).opacity(0.25))
                                .foregroundColor(.white)
                                .clipShape(Capsule())
                        }
                    }
                    .padding(.leading, 4)
                }

                // Expanded Trailing: Estimated arrival time & due info
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 2) {
                        HStack(spacing: 4) {
                            Image(systemName: "flag.checkered")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                            Text(context.state.arrivalTime.isEmpty ? "--:--" : context.state.arrivalTime)
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .foregroundColor(.white)
                        }
                        if !context.state.dueText.isEmpty {
                            Text(context.state.dueText)
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.trailing, 4)
                }

                // Expanded Bottom: Step title, status, and clean progress indicator
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(context.state.title)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                            .lineLimit(1)

                        if !context.state.subtitle.isEmpty {
                            Text(context.state.subtitle)
                                .font(.system(size: 12, weight: .regular))
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }

                        ProgressView(value: max(0.0, min(1.0, context.state.progress)))
                            .tint(Color.accentOrWhite(hex: context.state.tintColorHex))
                            .padding(.top, 2)
                    }
                    .padding(.horizontal, 4)
                    .padding(.top, 4)
                }
            } compactLeading: {
                // Compact Leading (Left capsule edge): Transit icon + line chip
                HStack(spacing: 3) {
                    Image(systemName: iconForMode(context.state.modeName))
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(Color.accentOrWhite(hex: context.state.tintColorHex))
                    if !context.state.chip.isEmpty {
                        Text(context.state.chip)
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                    }
                }
                .padding(.leading, 2)
            } compactTrailing: {
                // Compact Trailing (Right capsule edge): ETA / Arrival time
                HStack(spacing: 2) {
                    Text(context.state.arrivalTime.isEmpty ? "--:--" : context.state.arrivalTime)
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
                .padding(.trailing, 2)
            } minimal: {
                // Minimal (Shared Island bubble): Transit icon in route tint
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
        VStack(spacing: 8) {
            HStack(alignment: .center) {
                // Transit icon + Line Badge
                HStack(spacing: 6) {
                    Image(systemName: iconForMode(state.modeName))
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(Color.accentOrWhite(hex: state.tintColorHex))
                    if !state.chip.isEmpty {
                        Text(state.chip)
                            .font(.system(size: 14, weight: .bold, design: .rounded))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.accentOrWhite(hex: state.tintColorHex).opacity(0.25))
                            .foregroundColor(.white)
                            .clipShape(Capsule())
                    }
                }

                Spacer()

                // Final Arrival Time
                HStack(spacing: 4) {
                    Image(systemName: "flag.checkered")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                    Text(state.arrivalTime.isEmpty ? "--:--" : state.arrivalTime)
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                }
            }

            // Step Details
            VStack(alignment: .leading, spacing: 2) {
                Text(state.title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                if !state.subtitle.isEmpty {
                    Text(state.subtitle)
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // Progress Bar
            ProgressView(value: max(0.0, min(1.0, state.progress)))
                .tint(Color.accentOrWhite(hex: state.tintColorHex))
        }
        .padding(14)
    }
}

private func iconForMode(_ mode: String) -> String {
    switch mode.lowercased() {
    case "bus": return "bus.fill"
    case "train": return "tram.fill.tunnel"
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
        var int: UInt64 = 0
        Scanner(string: clean).scanHexInt64(&int)
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
        var int: UInt64 = 0
        Scanner(string: clean).scanHexInt64(&int)
        guard clean.count == 6 else { return Color(red: 0.38, green: 0.65, blue: 1.0) }
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
