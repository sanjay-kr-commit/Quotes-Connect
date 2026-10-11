package connects.quotes.repository

import connects.quotes.model.Id
import connects.quotes.model.Notification

/**
 * Repository interface for managing incoming connection notifications.
 */
interface NotificationRepository {

    suspend fun getNotifications(authorId: Id, limit: Int = 50): List<Notification>

    suspend fun addNotification(notification: Notification)

    suspend fun markAsRead(notificationId: Id)
}
