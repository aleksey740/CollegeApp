package com.sakovich.collegeapp.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sakovich.collegeapp.data.models.Event
import kotlinx.coroutines.tasks.await
import java.util.Date

class EventRepository {
    private val db = FirebaseFirestore.getInstance()
    private val eventsCollection = db.collection("events")

    suspend fun getAllEvents(): List<Event> {
        return try {
            val query = eventsCollection
                .orderBy("date", Query.Direction.ASCENDING)
                .get()
                .await()

            query.documents.map { document ->
                document.toObject(Event::class.java)!!.copy(id = document.id)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addEvent(event: Event): String {
        return try {
            // Убираем ID, чтобы Firestore сгенерировал его автоматически
            val eventWithoutId = event.copy(id = "")
            val document = eventsCollection.add(eventWithoutId).await()
            document.id
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun updateEvent(eventId: String, event: Event): Boolean {
        return try {
            eventsCollection.document(eventId).set(event).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteEvent(eventId: String): Boolean {
        return try {
            eventsCollection.document(eventId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }


}