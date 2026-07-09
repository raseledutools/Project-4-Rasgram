package com.rasel.rasgram.utils

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.rasel.rasgram.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SyncManager(private val context: Context, private val currentUserMobile: String) {
    private val db = FirebaseFirestore.getInstance()
    private val appDb = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startSyncing() {
        syncUsers()
        syncGroups()
        syncPrivateMessages()
        // status sync could also be added here
    }

    private fun syncUsers() {
        db.collection("chat_users").addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                val users = snapshot.documents.mapNotNull { doc ->
                    try {
                        User(
                            uid = doc.id,
                            name = doc.getString("name") ?: "",
                            mobile = doc.getString("mobile") ?: "",
                            avatarUrl = doc.getString("avatarUrl") ?: "",
                            lastActive = doc.getLong("lastActive") ?: 0L,
                            typingTo = doc.getString("typingTo"),
                            statusVisible = doc.getBoolean("statusVisible") ?: true,
                            about = doc.getString("about") ?: "Hey there! I am using RasGram.",
                            fcmToken = doc.getString("fcmToken") ?: "",
                            isBlocked = doc.getBoolean("isBlocked") ?: false,
                            disappearingTimer = doc.getLong("disappearingTimer") ?: 0L
                        )
                    } catch (ex: Exception) {
                        null
                    }
                }
                appDb.userDao().insertUsers(users)
            }
        }
    }

    private fun syncGroups() {
        db.collection("groups")
            .whereArrayContains("members", currentUserMobile)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                scope.launch {
                    val groups = snapshot.documents.mapNotNull { doc ->
                        try {
                            Group(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                avatarUrl = doc.getString("avatarUrl") ?: "",
                                description = doc.getString("description") ?: "",
                                members = (doc.get("members") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                admins = (doc.get("admins") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                typingUsers = (doc.get("typingUsers") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                createdBy = doc.getString("createdBy") ?: "",
                                createdAt = doc.getLong("createdAt") ?: 0L,
                                disappearingTimer = doc.getLong("disappearingTimer") ?: 0L
                            )
                        } catch (ex: Exception) {
                            null
                        }
                    }
                    appDb.groupDao().insertGroups(groups)

                    // Sync messages for each group
                    groups.forEach { group ->
                        syncGroupMessages(group.id)
                    }
                }
            }
    }

    private fun syncGroupMessages(groupId: String) {
        db.collection("group_msg_$groupId").addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            processMessages(snapshot)
        }
    }

    private fun syncPrivateMessages() {
        // Find all chats where I am involved.
        // The current Firestore structure uses pvt_msg_{chatId}. 
        // We might need to query by senderMobile and receiverMobile if we don't have a list of chatIds.
        // Wait, RasGram uses generateChatId(mobile1, mobile2)
        // Since we can't do a wildcard collection group query easily without an index, 
        // we can listen to "messages" if it was a flat collection, but it's sharded.
        
        // Let's iterate over all users and sync the chat with each.
        scope.launch {
            appDb.userDao().getAllUsers().collect { users ->
                users.forEach { user ->
                    if (user.mobile != currentUserMobile) {
                        val chatId = com.rasel.rasgram.generateChatId(currentUserMobile, user.mobile)
                        db.collection("pvt_msg_$chatId").addSnapshotListener { snapshot, e ->
                            if (e != null || snapshot == null) return@addSnapshotListener
                            processMessages(snapshot)
                        }
                    }
                }
            }
        }
    }

    private fun processMessages(snapshot: QuerySnapshot) {
        scope.launch {
            val messages = snapshot.documents.mapNotNull { doc ->
                try {
                    Message(
                        id = doc.id,
                        text = doc.getString("text") ?: "",
                        senderMobile = doc.getString("senderMobile") ?: "",
                        receiverMobile = doc.getString("receiverMobile") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        timeString = doc.getString("timeString") ?: "",
                        fileUrl = doc.getString("fileUrl"),
                        fileName = doc.getString("fileName"),
                        fileType = doc.getString("fileType"),
                        fileSizeBytes = doc.getLong("fileSizeBytes") ?: 0L,
                        thumbnailUrl = doc.getString("thumbnailUrl"),
                        reaction = doc.getString("reaction"),
                        read = doc.getBoolean("read") ?: false,
                        delivered = doc.getBoolean("delivered") ?: false,
                        isCallLog = doc.getBoolean("isCallLog") ?: false,
                        callStatus = doc.getString("callStatus"),
                        callType = doc.getString("callType"),
                        isPending = doc.metadata.hasPendingWrites(),
                        replyToId = doc.getString("replyToId"),
                        replyToText = doc.getString("replyToText"),
                        replyToSender = doc.getString("replyToSender"),
                        isDeleted = doc.getBoolean("isDeleted") ?: false,
                        isForwarded = doc.getBoolean("isForwarded") ?: false,
                        isStarred = doc.getBoolean("isStarred") ?: false,
                        duration = (doc.getLong("duration") ?: 0L).toInt(),
                        // waveform float list would be more complex to map safely from Any, skipping for sync simplicity
                        waveform = emptyList() 
                    )
                } catch (ex: Exception) {
                    null
                }
            }
            appDb.messageDao().insertMessages(messages)
        }
    }
}
