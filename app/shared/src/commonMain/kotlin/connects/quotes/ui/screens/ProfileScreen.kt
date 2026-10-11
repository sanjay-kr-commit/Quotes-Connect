package connects.quotes.ui.screens

import androidx.compose.runtime.Composable
import connects.quotes.model.Credentials
import connects.quotes.model.Id
import connects.quotes.model.Passage

/**
 * Legacy compatibility alias for AuthorScreen.
 * Renamed to AuthorScreen to reflect author sovereign identity, inactivity periods, and discard settings.
 */
@Composable
fun ProfileScreen(
    currentIdentity: Credentials?,
    authorPassage: Passage?,
    isAuthorAdvertised: Boolean,
    createdPassages: List<Passage>,
    onInspectPassage: (Id) -> Unit,
    onToggleAuthorAdvertise: () -> Unit,
    onOpenIdentityDialog: () -> Unit,
    onLogout: () -> Unit,
    onChangePassphrase: (String, String) -> Unit = { _, _ -> }
) {
    AuthorScreen(
        currentIdentity = currentIdentity,
        authorPassage = authorPassage,
        isAuthorAdvertised = isAuthorAdvertised,
        authorSettings = null,
        createdPassages = createdPassages,
        onInspectPassage = onInspectPassage,
        onToggleAuthorAdvertise = onToggleAuthorAdvertise,
        onOpenIdentityDialog = onOpenIdentityDialog,
        onLogout = onLogout,
        onChangePassphrase = onChangePassphrase
    )
}
