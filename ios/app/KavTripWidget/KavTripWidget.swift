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
        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded Leading: Transit mode icon + route chip
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 6) {
                        Image(systemName: iconForMode(context.state.modeName))
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(Color(hex: context.state.tintColorHex))

                        Text(context.state.chip)
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color(hex: context.state.tintColorHex).opacity(0.2))
                            .foregroundColor(Color(hex: context.state.tintColorHex))
                            .clipShape(Capsule())
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
                            Text(context.state.arrivalTime)
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .foregroundColor(.primary)
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
                            .foregroundColor(.primary)
                            .lineLimit(1)

                        if !context.state.subtitle.isEmpty {
                            Text(context.state.subtitle)
                                .font(.system(size: 12, weight: .regular))
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }

                        ProgressView(value: max(0.0, min(1.0, context.state.progress)))
                            .tint(Color(hex: context.state.tintColorHex))
                            .padding(.top, 2)
                    }
                    .padding(.horizontal, 4)
                    .padding(.top, 4)
                }
            } compactLeading: {
                // Compact Leading (Left capsule edge): Transit icon + line chip
                HStack(spacing: 4) {
                    Image(systemName: iconForMode(context.state.modeName))
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: context.state.tintColorHex))
                    Text(context.state.chip)
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundColor(.primary)
                }
            } compactTrailing: {
                // Compact Trailing (Right capsule edge): ETA / Arrival time
                Text(context.state.arrivalTime)
                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                    .foregroundColor(Color(hex: context.state.tintColorHex))
            } minimal: {
                // Minimal (Shared Island bubble): Transit icon in route tint
                Image(systemName: iconForMode(context.state.modeName))
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(Color(hex: context.state.tintColorHex))
            }
            .keylineTint(Color(hex: context.state.tintColorHex))
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
                        .foregroundColor(Color(hex: state.tintColorHex))
                    Text(state.chip)
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color(hex: state.tintColorHex).opacity(0.2))
                        .foregroundColor(Color(hex: state.tintColorHex))
                        .clipShape(Capsule())
                }

                Spacer()

                // Final Arrival Time
                HStack(spacing: 4) {
                    Image(systemName: "flag.checkered")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                    Text(state.arrivalTime)
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundColor(.primary)
                }
            }

            // Step Details
            VStack(alignment: .leading, spacing: 2) {
                Text(state.title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.primary)
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
                .tint(Color(hex: state.tintColorHex))
        }
        .padding(14)
        .background(Color.black.opacity(0.92))
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
            (r, g, b) = (0, 122, 255)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255.0,
            green: Double(g) / 255.0,
            blue: Double(b) / 255.0,
            opacity: 1.0
        )
    }
}
