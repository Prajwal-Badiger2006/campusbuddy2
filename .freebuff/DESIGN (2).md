---
name: Emerald Scholastic
colors:
  surface: '#f8f9ff'
  surface-dim: '#cbdbf6'
  surface-bright: '#f8f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#eff4ff'
  surface-container: '#e6eeff'
  surface-container-high: '#dde9ff'
  surface-container-highest: '#d3e3ff'
  on-surface: '#0b1c30'
  on-surface-variant: '#404944'
  inverse-surface: '#213146'
  inverse-on-surface: '#ebf1ff'
  outline: '#707974'
  outline-variant: '#bfc9c3'
  surface-tint: '#2b6954'
  primary: '#003527'
  on-primary: '#ffffff'
  primary-container: '#064e3b'
  on-primary-container: '#80bea6'
  inverse-primary: '#95d3ba'
  secondary: '#006c49'
  on-secondary: '#ffffff'
  secondary-container: '#6cf8bb'
  on-secondary-container: '#00714d'
  tertiary: '#0f00a3'
  on-tertiary: '#ffffff'
  tertiary-container: '#2c2abc'
  on-tertiary-container: '#a8aaff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#b0f0d6'
  primary-fixed-dim: '#95d3ba'
  on-primary-fixed: '#002117'
  on-primary-fixed-variant: '#0b513d'
  secondary-fixed: '#6ffbbe'
  secondary-fixed-dim: '#4edea3'
  on-secondary-fixed: '#002113'
  on-secondary-fixed-variant: '#005236'
  tertiary-fixed: '#e1e0ff'
  tertiary-fixed-dim: '#c0c1ff'
  on-tertiary-fixed: '#07006c'
  on-tertiary-fixed-variant: '#2f2ebe'
  background: '#f8f9ff'
  on-background: '#0b1c30'
  surface-variant: '#d3e3ff'
typography:
  display:
    fontFamily: Outfit
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Outfit
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: Outfit
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  headline-md:
    fontFamily: Outfit
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  headline-sm:
    fontFamily: Outfit
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: Outfit
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Outfit
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-sm:
    fontFamily: Outfit
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-md:
    fontFamily: Outfit
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.05em
  code:
    fontFamily: monospace
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  base: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 48px
  container-max: 1280px
  gutter: 24px
---

## Brand & Style
The design system embodies a Corporate Modern Academic aesthetic, tailored for high-stakes educational management and collaborative research. It prioritizes clarity, intellectual rigor, and premium utility. Drawing inspiration from the functional density of GitHub and the spatial minimalism of Notion, the system utilizes a "structured canvas" approach. 

The emotional response is one of organized calm and institutional trust. The visual style is characterized by high information density without clutter, utilizing sharp execution, ample whitespace, and a sophisticated color palette to differentiate academic workstreams.

## Colors
The palette is anchored by "Deep Emerald," providing a sense of prestige and academic heritage. 

- **Primary (Deep Emerald):** Used for key branding, primary actions, and high-level navigation states.
- **Secondary (Mint Accent):** Used for success states and subtle highlights.
- **Tertiary (Indigo):** Reserved for interactive data points or secondary focus areas like links within documents.
- **Neutral (Midnight Navy):** The primary text color, chosen over pure black to maintain a softer, more premium feel against the blue-tinted background.
- **Background (Ice Blue):** A very light, cool-toned off-white that reduces eye strain during long reading sessions compared to pure white.

## Typography
This design system utilizes **Outfit** across all roles to maintain a modern, geometric clarity that feels contemporary yet professional. 

Headlines use tighter letter-spacing and heavier weights to create a strong hierarchy. Body text is optimized for legibility with generous line-heights. Labels and metadata use uppercase styling with increased letter spacing to differentiate them from prose. For technical or academic notations, a system-default monospace font is used to provide a "developer-tool" level of precision.

## Layout & Spacing
The layout philosophy is based on a **Fixed Grid** for desktop to ensure content remains readable and focused, transitioning to a fluid model for mobile.

- **Grid:** A 12-column grid is used for desktop (1280px max-width) with 24px gutters.
- **Rhythm:** An 8px linear scale governs all spatial relationships. 
- **Sidebars:** Inspired by Notion, navigation is often housed in a fixed-width left sidebar (240px-280px) to maximize vertical scanning.
- **Mobile:** Margins reduce to 16px, and columns collapse into a single-column stack. Heavy use of inset padding (16px) for cards ensures content feels contained.

## Elevation & Depth
This design system avoids heavy drop shadows in favor of **Tonal Layers** and **Subtle Outlines**. Depth is communicated through stacking order rather than light source simulation.

1.  **Level 0 (Base):** The Ice Blue background (`#f8f9ff`).
2.  **Level 1 (Card/Container):** Pure white (`#ffffff`) surfaces with a 1px border in a muted neutral (`#e2e8f0`). Shadows are avoided here.
3.  **Level 2 (Popovers/Modals):** Pure white surfaces with a very soft, highly diffused ambient shadow (0px 4px 20px rgba(11, 28, 48, 0.08)) to indicate temporary interaction.
4.  **Interactive States:** Hovering over an element typically results in a slight background color shift (e.g., to a very light grey) rather than an elevation lift.

## Shapes
Following the "Soft" configuration, the design system uses a conservative corner radius to maintain a professional, academic rigor while avoiding the harshness of sharp 90-degree angles.

- **Standard Elements (Buttons, Inputs, Cards):** 0.25rem (4px).
- **Larger Containers (Modals):** 0.5rem (8px).
- **Icons & Avatars:** Avatars should be circular to provide a soft counter-point to the otherwise geometric layout. Icons should use a 1.5pt to 2pt stroke weight with slightly rounded caps.

## Components
- **Buttons:** Primary buttons are solid Deep Emerald with white text. Secondary buttons use a "ghost" style: 1px border of the primary color with primary color text.
- **Inputs:** Fields are white with a 1px neutral border. On focus, the border transitions to Deep Emerald with a 2px "halo" (soft glow) of the same color at 10% opacity.
- **Chips/Tags:** Small, low-contrast pills. Backgrounds should be 5-10% opacity of the category color (e.g., Secondary Mint) with the text being the full-strength color.
- **Lists:** Inspired by GitHub, lists should use horizontal dividers (1px) with generous padding (12px-16px) and a subtle hover state transition.
- **Cards:** White background, 1px border (`#e2e8f0`), no shadow. Titles within cards should use `headline-sm`.
- **Breadcrumbs:** Vital for academic navigation. Use `body-sm` with a separator icon (chevron-right), maintaining a clear path back to the dashboard.
- **Progress Indicators:** Linear bars using the Secondary Mint color to show course or task completion.