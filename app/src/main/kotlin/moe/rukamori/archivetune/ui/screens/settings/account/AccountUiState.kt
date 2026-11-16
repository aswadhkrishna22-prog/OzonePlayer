/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.account

sealed interface AccountStatus {
    data object Loading : AccountStatus
    data object LoggedOut : AccountStatus
    data class LoggedIn(
        val name: String,
        val email: String,
        val handle: String,
        val avatarUrl: String?,
        val hasMultipleAccounts: Boolean
    ) : AccountStatus
}

data class AccountUiState(
    val status: AccountStatus = AccountStatus.Loading,
    val extractedColorHex: String? = null,
    val isExtractingColor: Boolean = false
)
