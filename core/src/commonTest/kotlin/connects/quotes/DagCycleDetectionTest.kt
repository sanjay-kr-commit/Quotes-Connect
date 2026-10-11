package connects.quotes

import connects.quotes.graph.DagValidator
import connects.quotes.model.Id
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagCycleDetectionTest {

    @Test
    fun testSelfSubscriptionCreatesCycle() = runTest {
        val a = Id("a")
        assertTrue(DagValidator.wouldCreateCycle(a, a) { emptyList() })
    }

    @Test
    fun testDirectCycleDetected() = runTest {
        val a = Id("a")
        val b = Id("b")

        // b already subscribes to a: b -> a
        val graph = mapOf(b to listOf(a))

        // now a tries to subscribe to b: a -> b
        // This would close a loop: a -> b -> a
        val createsCycle = DagValidator.wouldCreateCycle(a, b) { id -> graph[id] ?: emptyList() }
        assertTrue(createsCycle)
    }

    @Test
    fun testTransitiveCycleDetected() = runTest {
        val a = Id("a")
        val b = Id("b")
        val c = Id("c")
        val d = Id("d")

        // Chain: d -> c -> b -> a
        val graph = mapOf(
            d to listOf(c),
            c to listOf(b),
            b to listOf(a)
        )

        // a tries to subscribe to d: a -> d -> c -> b -> a
        val createsCycle = DagValidator.wouldCreateCycle(a, d) { id -> graph[id] ?: emptyList() }
        assertTrue(createsCycle)
    }

    @Test
    fun testValidAcyclicAdditionAllowed() = runTest {
        val a = Id("a")
        val b = Id("b")
        val c = Id("c")

        // a -> b, and now c -> b
        val graph = mapOf(
            a to listOf(b)
        )

        val createsCycle = DagValidator.wouldCreateCycle(c, b) { id -> graph[id] ?: emptyList() }
        assertFalse(createsCycle)
    }
}
