/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.viewmodels.AboutScreenState

internal object AboutContract {
    const val SOLANA_ADDRESS = "DT3ckdbNuiQMR1mrCpBXCMrhLB19GckVv3YfxsLLiF8z"
    const val SOLANA_LABEL = "Solana (SOL)"
    const val UPSTREAM_REPO_URL = "https://github.com/Rukamori/ArchiveTune"
    const val GPL_LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
    const val CREDITS_NOTICE = "Based on ArchiveTune by Rukamori and contributors"
    const val DISCLAIMER_NOTICE = "Unofficial app, not affiliated with YouTube, Google, Spotify or Apple."
    const val GPL_LICENSE_NOTICE = "Based on ArchiveTune by Rukamori and contributors.\nSource code available under GPL-3.0."

    const val GPL_V3_NOTICE_TEXT = """GNU GENERAL PUBLIC LICENSE
Version 3, 29 June 2007

Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>
Everyone is permitted to copy and distribute verbatim copies of this license document, but changing it is not allowed.

Preamble
The GNU General Public License is a free, copyleft license for software and other kinds of works.

The licenses for most software and other practical works are designed to take away your freedom to share and change the works. By contrast, the GNU General Public License is intended to guarantee your freedom to share and change all versions of a program--to make sure it remains free software for all its users. We, the Free Software Foundation, use the GNU General Public License for most of our software; it applies also to any other work released this way by its authors. You can apply it to your programs, too.

When we speak of free software, we are referring to freedom, not price. Our General Public Licenses are designed to make sure that you have the freedom to distribute copies of free software (and charge for them if you wish), that you receive source code or can get it if you want it, that you can change the software or use pieces of it in new free programs, and that you know you can do these things.

To protect your rights, we need to prevent others from denying you these rights or asking you to surrender the rights. Therefore, you have certain responsibilities if you distribute copies of the software, or if you modify it: responsibilities to respect the freedom of others.

For example, if you distribute copies of such a program, whether gratis or for a fee, you must pass on to the recipients the same freedoms that you received. You must make sure that they, too, receive or can get the source code. And you must show them these terms so they know their rights.

Developers that use the GNU GPL protect your rights with two steps: (1) assert copyright on the software, and (2) offer you this License giving you legal permission to copy, distribute and/or modify it.

For the developers' and authors' protection, the GPL clearly explains that there is no warranty for this free software. For both users' and authors' sake, the GPL requires that modified versions be marked as changed, so that their problems will not be attributed erroneously to authors of previous versions.

Ozone Player and ArchiveTune are distributed in the hope that they will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details."""
}

typealias AboutUiState = AboutScreenState

@Immutable
data class AboutUiActions(
    val onNavigateUp: () -> Unit = {},
    val onNavigateHome: () -> Unit = {},
    val onOpenUri: (String) -> Unit = {},
    val onShowOverflowMenu: () -> Unit = {},
    val onDismissOverflowMenu: () -> Unit = {},
    val onOpenTranslationContributors: () -> Unit = {},
    val onOpenDependencyLicenses: () -> Unit = {},
    val onOpenGplLicense: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
    val onRetryTranslationContributors: () -> Unit = {},
    val onRetryDependencyLicenses: () -> Unit = {},
    val onRetryContributors: () -> Unit = {},
)

typealias AboutActions = AboutUiActions
