package connects.quotes.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeystoreStateTest {
    @Test
    fun lifecycleTransitionsAreExplicit() {
        assertEquals(KeystoreState.AVAILABLE, KeystoreState.ABSENT.transition(KeystoreTransition.CREATE))
        assertEquals(KeystoreState.LOCKED, KeystoreState.AVAILABLE.transition(KeystoreTransition.LOCK))
        assertEquals(KeystoreState.AVAILABLE, KeystoreState.LOCKED.transition(KeystoreTransition.UNLOCK))
        assertEquals(KeystoreState.ABSENT, KeystoreState.AVAILABLE.transition(KeystoreTransition.DELETE))
        assertEquals(KeystoreState.ABSENT, KeystoreState.LOCKED.transition(KeystoreTransition.DELETE))
    }

    @Test
    fun onlyAbsentMayCreate() {
        assertFailsWith<IllegalStateException> {
            KeystoreState.AVAILABLE.transition(KeystoreTransition.CREATE)
        }
        assertFailsWith<IllegalStateException> {
            KeystoreState.LOCKED.transition(KeystoreTransition.CREATE)
        }
    }

    @Test
    fun invalidRecoveryStatesCannotSilentlyBecomeAvailable() {
        for (state in listOf(
            KeystoreState.CORRUPT,
            KeystoreState.UNSUPPORTED,
            KeystoreState.UNAVAILABLE,
        )) {
            assertFailsWith<IllegalStateException> {
                state.transition(KeystoreTransition.UNLOCK)
            }
        }
    }

    @Test
    fun exceptionalStatesMayBeDeletedExplicitly() {
        assertEquals(KeystoreState.ABSENT, KeystoreState.CORRUPT.transition(KeystoreTransition.DELETE))
        assertEquals(KeystoreState.ABSENT, KeystoreState.UNSUPPORTED.transition(KeystoreTransition.DELETE))
        assertEquals(KeystoreState.ABSENT, KeystoreState.UNAVAILABLE.transition(KeystoreTransition.DELETE))
    }
}
