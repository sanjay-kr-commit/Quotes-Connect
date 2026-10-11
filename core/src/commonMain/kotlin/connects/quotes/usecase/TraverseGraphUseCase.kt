package connects.quotes.usecase

import connects.quotes.graph.GraphEngine
import connects.quotes.model.Id
import connects.quotes.repository.PassageRepository

/**
 * Use case for navigating and exploring the knowledge graph.
 */
class TraverseGraphUseCase(
    private val passageRepository: PassageRepository
) {
    /**
     * Builds a tree of descendants and sub-passages for a given root.
     */
    suspend fun getDescendantTree(rootId: Id, maxDepth: Int = 3): GraphEngine.TreeNode? {
        return GraphEngine.buildDescendantTree(
            rootId = rootId,
            maxDepth = maxDepth,
            getPassage = { passageRepository.get(it) },
            getSubscribers = { passageRepository.getSubscribers(it) }
        )
    }

    /**
     * Finds the shortest connection between two passages.
     */
    suspend fun findPath(startId: Id, endId: Id): List<Id> {
        return GraphEngine.findShortestPath(
            startId = startId,
            endId = endId,
            getNeighbors = { passageRepository.getSubscribedTo(it) + passageRepository.getSubscribers(it) }
        )
    }
}
