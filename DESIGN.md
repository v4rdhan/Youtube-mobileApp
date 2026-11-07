---
name: StreamShield
description: A quiet native Android shell for live YouTube viewing and request protection.
colors:
  primary: "#B4E098"
  on-primary: "#173807"
  primary-container: "#2D501F"
  on-primary-container: "#CFF5B5"
  secondary: "#C2CCB7"
  on-secondary: "#2D3528"
  secondary-container: "#414B3A"
  on-secondary-container: "#DEE8D2"
  surface: "#121510"
  surface-variant: "#42483D"
  on-surface: "#E3E6DC"
  on-surface-variant: "#C3C9BA"
  outline: "#8D9485"
typography:
  headline-medium:
    fontFamily: "sans"
  title-large:
    fontFamily: "sans"
  title-medium:
    fontFamily: "sans"
  body-medium:
    fontFamily: "sans"
spacing:
  body-inset: "8dp"
  control-gap: "12dp"
  search-gutter: "16dp"
  page-top: "20dp"
  page-gutter: "24dp"
  section-gap: "28dp"
  page-bottom: "32dp"
components:
  button-filled:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
  button-outlined:
    textColor: "{colors.primary}"
  input-outlined:
    textColor: "{colors.on-surface}"
  native-page:
    backgroundColor: "{colors.surface}"
    padding: "20dp 24dp 32dp"
  body-copy:
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.body-medium}"
    padding: "8dp 0dp"
---

# Design System: StreamShield

## Overview

**Creative North Star: "Quiet viewing room"**

StreamShield surrounds live YouTube content with a restrained native Android interface. Charcoal-green surfaces recede around video; pale green identifies protection and native actions. Android Material 3 supplies controls, type hierarchy, and interaction feedback.

Native screens use readable vertical sections and open space. The live site's imagery and playback interface lead the viewing experience. This document records the implemented phone interface; it does not establish a tablet or desktop layout.

**Key Characteristics:**

- A dark charcoal-green shell with pale-green actions.
- Native Material 3 typography and controls.
- Live video and site content as the principal imagery.
- Scrollable native settings and saved links with explicit state feedback.

Ground truth: `app/src/main/res/values/themes.xml`, `res/layout/activity_main.xml`, and `java/app/streamshield/MainActivity.kt`. Visual evidence: the five Android 15 phone captures under `.impeccable/review/` (viewing, Protection, Saved, large text, and fullscreen). Frontmatter records app-defined values; inherited Material defaults remain owned by the Android theme. Dimensions use dp, and text follows Android font scaling.

## Colors

The palette pairs a near-black green ground with soft green actions and warm grey-green text. Exact values are normative in frontmatter.

### Primary

- **Pale Leaf** (`primary`) fills native action buttons and the enabled protection icon; **Deep Leaf Ink** (`on-primary`) supplies their foreground.
- **Forest Container** and **Light Leaf Ink** (`primary-container`, `on-primary-container`) supply inherited Material container roles.

### Secondary

- **Muted Sage** and **Dark Sage Ink** (`secondary`, `on-secondary`) supply Material's secondary foreground roles.
- **Moss Container** and **Light Sage Ink** (`secondary-container`, `on-secondary-container`) support secondary component states, including the bottom navigation's selected indicator.

### Neutral

- **Charcoal Green** (`surface`) grounds the window, native pages, and WebView before content arrives.
- **Grey Moss** (`surface-variant`) supplies the alternate surface role.
- **Soft Chalk** (`on-surface`) carries native headings and principal text.
- **Muted Chalk** (`on-surface-variant`) carries explanatory copy and the inactive or unavailable protection icon.
- **Sage Outline** (`outline`) defines outlined controls through the Material theme.

**The Action Color Rule.** Use the primary green for native actions and protection state; let live YouTube content retain its own colors.

## Typography

The app uses Android's `sans` family and Material 3 text appearances, with no custom font files or numeric type overrides. Sizes, weights, line heights, and component labels inherit from Material and Android.

- **Headline Medium:** protection status and the Saved page heading.
- **Title Large:** toolbar titles, the Saved empty-state title, and connection-error title.
- **Title Medium:** settings section headings.
- **Body Medium:** explanations and session/filter status, using the quieter foreground role.
- **Body Large:** the centered preparation message.

Buttons use sentence case. Saved titles align to the start and wrap to at most three lines. Native font scaling and scrollable content accommodate enlarged text.

## Layout

The phone shell is one vertical column: toolbar with minimum height (64dp), outlined search, loading indicator (2dp track), flexible content area, and labeled bottom navigation. Search has horizontal gutters (16dp) and a bottom gap (8dp). System bars, cutouts, and the keyboard contribute root padding.

Protection and Saved replace the content area with a native scroll view and hide search. Page padding is top (20dp), horizontal (24dp), and bottom (32dp). Related controls use top gaps (12dp); new sections use larger gaps (28dp). Body paragraphs have vertical inset (8dp). Protection hides bottom navigation; Saved retains it. Native pages have a toolbar back arrow.

Saved rows pair a flexible outlined title button with a remove icon button (56dp square), separated by a gap (8dp). The title button has minimum height (64dp). General filled actions have minimum height (48dp); the protection switch row has minimum height (64dp).

Fullscreen hides the entire native shell and system bars, uses sensor landscape orientation, and restores the shell on exit. There are no app-defined layout breakpoints. Validation covers phone, including large text and landscape playback.

## Elevation & Depth

The app adds no custom shadow or elevation tokens. Tonal surfaces, outlines, and the Material navigation indicator establish grouping. Toolbar, popup menu, snackbar, and interaction feedback retain inherited Material behavior. Native settings use an open column rather than raised cards.

## Shapes

Material 3 owns shapes; the app supplies no custom radius overrides. In the validated phone rendering, filled actions and saved title buttons have capsule silhouettes, outlined fields have gently rounded rectangular corners, and selected navigation icons sit on rounded tonal indicators. Line icons are Android vector drawables.

## Components

### Buttons

Filled Material buttons carry filter updates, custom-rule application, and retry. They use primary/on-primary colors and sentence case. Saved links use outlined buttons; removal uses Material icon buttons with descriptive accessibility labels. Ripple, pressed, focus, and disabled feedback are inherited from Material. There are no custom hover animations.

### Inputs / Fields

Search is a single-line outlined Material field with an end search icon and keyboard Search action. It accepts a search term or YouTube link. Invalid input appears through the native field error presentation.

Custom rules use a multiline outlined field with three to eight visible lines. Oversized input uses the field error mechanism. Focus and error treatments remain inherited from Material.

### Navigation

Home, Shorts, and Saved form the labeled Material bottom navigation. Selected items use a tonal icon indicator and stronger label treatment. The toolbar carries the shield identity, Protection action, and overflow menu. Native pages add a back arrow and change the title. The Protection action is primary green when protection is enabled and ready, and muted otherwise.

### Protection controls and feedback

The page starts with text status and session count, then a labeled Material switch. The switch is unavailable until the engine is ready. During filter updates, the button stays in place, changes its label to “Updating filters…”, and becomes disabled. Native snackbars report completion and failure. Filter status shows built-in rules or the last update time.

### Loading, errors, and saved content

Preparation uses a centered native message; page loading uses the thin progress indicator above content. Connection errors replace the content area with a centered title, explanation, and retry action. Empty Saved content explains how to save a link. Populated rows use real local titles with a separate remove action.

## Do's and Don'ts

### Do:

- **Do** reuse Material 3 components and existing theme roles.
- **Do** retain the primary-green action and protection role.
- **Do** use native scrolling, font scaling, and system insets on phone screens.
- **Do** preserve real content and explicit loading, disabled, empty, and failure states.

### Don't:

- **Don't** replace live YouTube content with a fabricated feed or static playback mockup.
- **Don't** add custom font, corner, shadow, or motion tokens without implementation evidence.
- **Don't** apply native-shell palette rules to YouTube's own rendered interface.
- **Don't** treat phone validation as evidence for tablet or desktop layouts.
