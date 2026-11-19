---
version: 1
slug: "app-src-main-java-app-streamshield-mainactivity-kt"
primary_target: "app/src/main/java/app/streamshield/MainActivity.kt"
related_targets: ["app/src/main/assets/youtube-theme.css","app/src/main/res/layout/activity_main.xml","app/src/main/res/menu/navigation.xml"]
---

# Reference-led Android redesign

Mode: Operate. Target: `app/src/main/java/app/streamshield/MainActivity.kt`, its native resources, and the live YouTube presentation stylesheet.

## Direction contract

THESIS: Match the user's supplied dark YouTube phone designs. Live browsing, search, playback, background audio, protection, and saved videos remain real app capabilities.

OWN-WORLD: Neutral #111111 background, #262626 navigation panel with 28dp top corners, #FAFAFA selected content, readable #ABABAB secondary content, and #DF1F26 primary actions. Android sans typography, restrained line icons, and 16px video-thumbnail corners.

STORY: Browse Home, discover videos through Explore, visit Channels, and keep local saved links in Library. Library's toolbar holds protection and page actions; Shorts remains available inside Library. No create or add action appears in navigation.

FIRST VIEWPORT: YouTube owns the working header, account controls, search, video content, and player. The flexible live content region sits above a compact four-item native navigation panel. Equal-width Home, Explore, Channels, and Library targets keep labels visible. The selected destination uses white foreground; inactive destinations use gray. Native ripples provide touch feedback; video imagery remains the focal content.

FORM: seed: none (pinned). User: “make design like this” and “i don't need that add button on the bottom nav menu in the given design”, with three attached reference boards specifying the phone screens and four destination labels.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance.

## Implementation boundary

The site supplies live thumbnails, titles, accounts, and playback; it is not replaced by a fabricated reference feed. Styling is independent of the protection toggle. No generated or downloaded raster assets ship with this change. Existing DESIGN.md describes the previous green interface and is preserved until an explicit documentation decision; this brief records the requested replacement direction.

## Navigation icons

Match the supplied reference silhouettes: a rounded pentagonal house with a short door mark, a rounded hexagonal compass with a diagonal lens, paired circular heads with oval bodies, and stacked rounded video-library frames. All four destinations have outlined inactive and filled active vector drawables. Use a consistent 1.25dp stroke on a 24dp canvas; preserve transparent internal details through native icon tinting with even-odd paths. Native Back uses the reference's small chevron, and More uses three small solid dots. Touch targets and the four-item navigation remain unchanged.

## Topic controls and feed refinement, 19 November 2026

The user's new phone screenshot shows oversized circular topic backgrounds behind the site's own rectangular buttons. Follow the compact controls in `C:/Users/arjun/Downloads/Crisp YouTube Dark UI Mockup.png`: one charcoal surface per category, 10px corners, 12px horizontal padding, 8px gaps, and medium-weight labels. A 32px visible surface sits inside a 48px touch target. Selected categories keep a slightly lighter charcoal fill and a subtle inset outline; selection continues to come from YouTube's real state. Nested site buttons are transparent so their default selected fill cannot overlap the app's shape. Keep horizontal scrolling and real category actions.

Match the same reference's inset, rounded video artwork and compact metadata. Retain the existing four destinations and add a quiet 1dp outline to the charcoal native navigation panel. This is a refinement of the established reference direction; the pre-existing DESIGN.md discrepancy remains outside this change.
