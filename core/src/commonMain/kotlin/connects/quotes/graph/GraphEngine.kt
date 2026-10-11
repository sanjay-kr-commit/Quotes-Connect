package connects.quotes.graph

import connects.quotes.model.Id
import connects.quotes.model.Passage
import kotlinx.serialization.Serializable

/**
 * Traversal and pathfinding engine for the Quotes Connect knowledge graph.
 */
object GraphEngine {

    /**
     * Represents a node with its tree of sub-passages (subscribers/comments).
     */
    @Serializable
    data class TreeNode(
        val passage: Passage,
        val children: List<TreeNode> = emptyList()
    )

    /**
     * Represents a flattened entry in a Linux `tree`-style hierarchy.
     */
    @Serializable
    data class FlatTreeItem(
        val passage: Passage,
        val prefix: String, // e.g. "├── " or "│   └── "
        val depth: Int,
        val isLast: Boolean
    )

    /**
     * Flattens the descendant nodes of this [TreeNode] into an ordered list with
     * standard Linux `tree` command formatting prefixes ("├── ", "└── ", "│   ", "    ").
     */
    fun TreeNode.flattenDescendants(): List<FlatTreeItem> {
        val result = mutableListOf<FlatTreeItem>()
        flattenRecursive(this.children, "", 1, result)
        return result
    }

    private fun flattenRecursive(
        children: List<TreeNode>,
        ancestorPrefix: String,
        depth: Int,
        result: MutableList<FlatTreeItem>
    ) {
        val count = children.size
        for (i in 0 until count) {
            val child = children[i]
            val isLast = (i == count - 1)
            val branchConnector = if (isLast) "└── " else "├── "
            val fullPrefix = ancestorPrefix + branchConnector
            result.add(FlatTreeItem(child.passage, fullPrefix, depth, isLast))

            val nextAncestorPrefix = ancestorPrefix + (if (isLast) "    " else "│   ")
            flattenRecursive(child.children, nextAncestorPrefix, depth + 1, result)
        }
    }

    /**
     * Recursively builds a descendant tree up to [maxDepth] and [maxNodes].
     */
    suspend fun buildDescendantTree(
        rootId: Id,
        maxDepth: Int = 3,
        maxNodes: Int = 200,
        getPassage: suspend (Id) -> Passage?,
        getSubscribers: suspend (Id) -> List<Id>
    ): TreeNode? {
        val root = getPassage(rootId) ?: return null
        val nodeCount = intArrayOf(1)
        return buildTreeRecursive(root, 0, maxDepth.coerceAtMost(8), maxNodes.coerceAtMost(500), nodeCount, getPassage, getSubscribers, mutableSetOf(rootId))
    }

    private suspend fun buildTreeRecursive(
        current: Passage,
        currentDepth: Int,
        maxDepth: Int,
        maxNodes: Int,
        nodeCount: IntArray,
        getPassage: suspend (Id) -> Passage?,
        getSubscribers: suspend (Id) -> List<Id>,
        visited: MutableSet<Id>
    ): TreeNode {
        if (currentDepth >= maxDepth || nodeCount[0] >= maxNodes) {
            return TreeNode(current, emptyList())
        }

        val childNodes = mutableListOf<TreeNode>()
        for (subId in getSubscribers(current.id)) {
            if (nodeCount[0] >= maxNodes) break
            if (visited.add(subId)) {
                val subPassage = getPassage(subId)
                if (subPassage != null) {
                    nodeCount[0]++
                    childNodes.add(
                        buildTreeRecursive(subPassage, currentDepth + 1, maxDepth, maxNodes, nodeCount, getPassage, getSubscribers, visited)
                    )
                }
            }
        }

        return TreeNode(current, childNodes)
    }

    /**
     * Finds the shortest connection path between two passages using BFS with hard traversal limits.
     * Enforces [maxDepth], [maxNodes], and [maxEdges] to protect against DoS and unbounded traversals (OPT-008).
     * Returns the ordered list of passage IDs from [startId] to [endId], or empty list if disconnected.
     */
    suspend fun findShortestPath(
        startId: Id,
        endId: Id,
        maxDepth: Int = 10,
        maxNodes: Int = 500,
        maxEdges: Int = 2000,
        getNeighbors: suspend (Id) -> List<Id>
    ): List<Id> {
        if (startId == endId) return listOf(startId)

        val queue = ArrayDeque<Pair<Id, Int>>()
        val cameFrom = mutableMapOf<Id, Id>()
        val visited = mutableSetOf<Id>()
        var edgeCount = 0

        queue.add(Pair(startId, 0))
        visited.add(startId)

        while (queue.isNotEmpty() && visited.size <= maxNodes && edgeCount <= maxEdges) {
            val (current, depth) = queue.removeFirst()
            if (current == endId) {
                // Reconstruct path
                val path = mutableListOf<Id>()
                var curr: Id? = endId
                while (curr != null) {
                    path.add(curr)
                    curr = cameFrom[curr]
                }
                return path.reversed()
            }

            if (depth >= maxDepth) continue

            for (neighbor in getNeighbors(current)) {
                edgeCount++
                if (edgeCount > maxEdges) break
                if (visited.add(neighbor)) {
                    cameFrom[neighbor] = current
                    queue.add(Pair(neighbor, depth + 1))
                    if (visited.size >= maxNodes) break
                }
            }
        }

        return emptyList()
    }
}
