/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.about.AboutContributor
import moe.rukamori.archivetune.about.AboutContributorCollection
import moe.rukamori.archivetune.about.AboutDependencyLicense
import moe.rukamori.archivetune.about.AboutDependencyLicenseCollection
import moe.rukamori.archivetune.about.AboutTranslationContributor
import moe.rukamori.archivetune.about.AboutTranslationContributorCollection
import moe.rukamori.archivetune.currentBuildHash

internal const val MaxDisplayedContributors = 20
internal const val ContributorsReadMoreUrl = "https://github.com/aswadhkrishna22-prog/OzonePlayer/graphs/contributors"

internal fun buildAboutUiModel(
    contributorsState: AboutContributorsUiState,
    dependencyLicensesState: AboutDependencyLicensesUiState,
    translationContributorsState: AboutTranslationContributorsUiState,
    isOverflowMenuExpanded: Boolean,
    activeDialog: AboutDialog,
): AboutUiModel = AboutUiModel(
    appNameResId = R.string.ui_app_name,
    versionName = BuildConfig.VERSION_NAME,
    buildHash = currentBuildHash,
    buildVariant = if (BuildConfig.DEBUG) "DEBUG" else BuildConfig.ARCHITECTURE.uppercase(),
    primaryLinks = AboutLinkCollection.of(
        AboutLinkUiModel(
            id = "github",
            iconResId = R.drawable.ic_github,
            labelResId = R.string.about_content_desc_github,
            url = "https://github.com/aswadhkrishna22-prog/OzonePlayer",
        ),
    ),
    leadDeveloper = TeamMember(
        avatarUrl = "https://github.com/aswadhkrishna22-prog.png",
        name = "Aswadh Krishna",
        positionResId = R.string.about_position_lead_dev,
        profileUrl = "https://github.com/aswadhkrishna22-prog",
        links = AboutLinkCollection.of(
            AboutLinkUiModel(
                id = "github",
                iconResId = R.drawable.ic_github,
                labelResId = R.string.about_content_desc_github,
                url = "https://github.com/aswadhkrishna22-prog",
            ),
        ),
    ),
    collaborators = TeamMemberCollection.Empty,
    respecters = TeamMemberCollection.Empty,
    contributorsState = AboutContributorsUiState.Empty,
    contributorsReadMoreUrl = ContributorsReadMoreUrl,
    dependencyLicensesState = dependencyLicensesState,
    translationContributorsState = translationContributorsState,
    isOverflowMenuExpanded = isOverflowMenuExpanded,
    activeDialog = activeDialog,
)

internal fun AboutContributorCollection.toUiCollection(): AboutContributorUiCollection {
    val contributors = ArrayList<AboutContributorUiModel>(MaxDisplayedContributors)
    forEach { contributor ->
        contributors.add(contributor.toUiModel())
    }
    return AboutContributorUiCollection.from(contributors)
}

internal fun AboutContributor.toUiModel(): AboutContributorUiModel =
    AboutContributorUiModel(
        login = login,
        avatarUrl = avatarUrl,
        profileUrl = profileUrl,
    )

internal fun AboutTranslationContributorCollection.toUiCollection(): AboutTranslationContributorUiCollection {
    val contributors = ArrayList<AboutTranslationContributorUiModel>(size)
    for (index in 0 until size) {
        contributors.add(this[index].toUiModel())
    }
    return AboutTranslationContributorUiCollection.from(contributors)
}

internal fun AboutTranslationContributor.toUiModel(): AboutTranslationContributorUiModel =
    AboutTranslationContributorUiModel(
        language = language,
        contributors = contributors.joinToString().takeIf(String::isNotBlank),
    )

internal fun AboutDependencyLicenseCollection.toUiCollection(): AboutDependencyLicenseUiCollection {
    val licenses = ArrayList<AboutDependencyLicenseUiModel>(size)
    for (index in 0 until size) {
        licenses.add(this[index].toUiModel())
    }
    return AboutDependencyLicenseUiCollection.from(licenses)
}

internal fun AboutDependencyLicense.toUiModel(): AboutDependencyLicenseUiModel =
    AboutDependencyLicenseUiModel(name = name, version = version, licenses = licenses)
