package com.devx.VisionCut.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devx.VisionCut.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onDone: () -> Unit,
    onGoPremium: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
    isPremium: Boolean
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .background(MaterialTheme.colorScheme.surfaceVariant)  // gray bg, cards sit on top
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text  = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Upgrade card ──────────────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clickable {
                    if (isPremium)
                        Toast.makeText(context, "Already subscribed! Enjoy 🎉", Toast.LENGTH_SHORT).show()
                    else
                        onGoPremium()
                },
            shape  = RoundedCornerShape(43.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Row(
                modifier          = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // White circle + black icon — intentional design on the purple card, not themed
                Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = Color.White) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint     = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Upgrade to Pro",
                        color      = MaterialTheme.colorScheme.onPrimary,
                        style      = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Access Premium Features Instantly",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // White circle + black chevron — same intentional design
                Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = Color.White) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.NorthEast,
                            contentDescription = null,
                            tint     = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Share / Support / Rate card ───────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(24.dp),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                SettingsItem(icon = Icons.Default.Share,       title = "Share App",            showDivider = true,  onClick = { viewModel.shareApp() })
                SettingsItem(icon = Icons.Default.HelpOutline, title = "Contact Support",       showDivider = true,  onClick = { viewModel.contactSupport() })
                SettingsItem(icon = Icons.Default.Star,        title = "Rate us on App Store",  showDivider = false, onClick = { viewModel.rateApp() })
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Privacy / Terms card ──────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(24.dp),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                SettingsItem(icon = Icons.Default.Security,    title = "Privacy Policy",   showDivider = true,  onClick = { viewModel.openPrivacyPolicy() })
                SettingsItem(icon = Icons.Default.Description, title = "Terms of Service", showDivider = false, onClick = { viewModel.openTermsOfService() })
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape    = RoundedCornerShape(12.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text     = title,
                modifier = Modifier.weight(1f),
                style    = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color    = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier  = Modifier.padding(start = 76.dp),
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}