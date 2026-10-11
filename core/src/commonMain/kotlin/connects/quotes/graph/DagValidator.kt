package connects.quotes.graph

import connects.quotes.model.Id

/**
 * Validates Directed Acyclic Graph (DAG) constraints.
 * Ensures the knowledge graph remains strictly acyclic with no circular subscriptions.
 */
object DagValidator {

    /**
     * Checks if adding a directed edge from [sourceId] to [targetId] would introduce a cycle.
     *
     * In Quotes Connect, an edge means [sourceId] subscribes to [targetId].
     * If [targetId] can already reach [sourceId] through existing subscriptions,
     * adding [sourceId] -> [targetId] would close a loop, which is forbidden.
     *
     * @param sourceId The passage proposing to subscribe.
     * @param targetId The passage being subscribed to.
     * @param getOutgoing Function returning all IDs that a given node subscribes to.
     * @return `true` if a cycle would be created, `false` if the edge is safe.
     */
    suspend fun wouldCreateCycle(
        sourceId: Id,
        targetId: Id,
        getOutgoing: suspend (Id) -> List<Id>
    ): Boolean {
        // Self-subscription is an immediate cycle of length 1
        if (sourceId == targetId) return true

        // Check if targetId can reach sourceId
        val visited = mutableSetOf<Id>()
        val queue = ArrayDeque<Id>()

        queue.add(targetId)
        visited.add(targetId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == sourceId) {
                return true // Cycle detected!
            }

            for (neighbor in getOutgoing(current)) {
                if (neighbor == sourceId) {
                    return true
                }
                if (visited.add(neighbor)) {
                    queue.add(neighbor)
                }
            }
        }

        return false
    }
}
