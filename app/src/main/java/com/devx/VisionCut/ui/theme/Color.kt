package com.devx.VisionCut.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand primary ─────────────────────────────────────────────────────────────
// NOTE: Two values were used across screens (0xFF6901FC and 0xFF6200EE).
//       Both are now unified here. Change BrandPurple once to update everywhere.
val BrandPurple      = Color(0xFF6901FC)  // main brand color — light mode primary
val BrandPurpleLight = Color(0xFF9747FF)  // lighter variant — dark mode primary

// ── Primary container (icon bg, badge bg) ─────────────────────────────────────
val PrimaryContainerLight     = Color(0xFFF3F0FE)
val PrimaryContainerDark      = Color(0xFF2D1A52)
val OnPrimaryContainerLight   = Color(0xFF21005D)
val OnPrimaryContainerDark    = Color(0xFFE9DDFF)

// ── Background ────────────────────────────────────────────────────────────────
val BackgroundLight = Color(0xFFFFFFFF)
val BackgroundDark  = Color(0xFF121212)

// ── Surface (cards, sheets, elevated layers) ──────────────────────────────────
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark  = Color(0xFF1E1E1E)

// ── Surface variant (settings bg, toggle row, input rows, back button) ────────
val SurfaceVariantLight = Color(0xFFF5F6F8)
val SurfaceVariantDark  = Color(0xFF2A2A2A)

// ── On-color text / icons ─────────────────────────────────────────────────────
val OnBackgroundLight = Color(0xFF1C1B1F)
val OnBackgroundDark  = Color(0xFFE6E1E5)

val OnSurfaceLight = Color(0xFF1C1B1F)
val OnSurfaceDark  = Color(0xFFE6E1E5)

// Secondary / muted text (replaces Color.Gray throughout the app)
val OnSurfaceVariantLight = Color(0xFF6B6B6B)
val OnSurfaceVariantDark  = Color(0xFF9E9E9E)

// ── Dividers / outlines ───────────────────────────────────────────────────────
val OutlineVariantLight = Color(0xFFEEEEEE)
val OutlineVariantDark  = Color(0xFF2E2E2E)