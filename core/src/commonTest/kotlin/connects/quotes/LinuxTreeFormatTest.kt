package connects.quotes

import connects.quotes.graph.GraphEngine
import connects.quotes.graph.GraphEngine.flattenDescendants
import connects.quotes.model.Id
import connects.quotes.model.PassageType
import connects.quotes.repository.InMemoryKnowledgeRepository
import connects.quotes.usecase.CreatePassageUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LinuxTreeFormatTest {

    @Test
    fun testLinuxTreeBranchFormatting() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val create = CreatePassageUseCase(repo, repo, repo, repo)

        // Root thread
        val root = create(
            type = PassageType.QUOTE,
            content = "Root philosophical thesis",
            parentId = null,
            currentTimeMillis = 1000L
        ).getOrThrow()

        // Child 1 (citing root)
        val c1 = create(
            type = PassageType.COMMENT,
            content = "First reaction comment",
            parentId = null,
            subscribeToId = root.id,
            currentTimeMillis = 1001L
        ).getOrThrow()

        // Sub-child 1.1 (citing child 1)
        val c1Sub = create(
            type = PassageType.COMMENT,
            content = "Nested reply to first comment",
            parentId = null,
            subscribeToId = c1.id,
            currentTimeMillis = 1002L
        ).getOrThrow()

        // Child 2 (citing root, second branch)
        val c2 = create(
            type = PassageType.QUOTE,
            content = "Second related quotation",
            parentId = null,
            subscribeToId = root.id,
            currentTimeMillis = 1003L
        ).getOrThrow()

        // Build descendant tree for root
        val tree = GraphEngine.buildDescendantTree(
            rootId = root.id,
            maxDepth = 4,
            getPassage = { repo.get(it) },
            getSubscribers = { repo.getSubscribers(it) }
        )

        assertNotNull(tree)
        assertEquals(root.id, tree.passage.id)

        // Flatten to Linux tree items
        val flatItems = with(GraphEngine) { tree.flattenDescendants() }
        assertEquals(3, flatItems.size)

        // Item 0: Child 1 (non-last child of root -> "├── ")
        assertEquals(c1.id, flatItems[0].passage.id)
        assertEquals("├── ", flatItems[0].prefix)
        assertEquals(1, flatItems[0].depth)

        // Item 1: Sub-child 1.1 (last child of non-last Child 1 -> "│   └── ")
        assertEquals(c1Sub.id, flatItems[1].passage.id)
        assertEquals("│   └── ", flatItems[1].prefix)
        assertEquals(2, flatItems[1].depth)

        // Item 2: Child 2 (last child of root -> "└── ")
        assertEquals(c2.id, flatItems[2].passage.id)
        assertEquals("└── ", flatItems[2].prefix)
        assertEquals(1, flatItems[2].depth)
    }
}
