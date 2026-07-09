package com.rasel.rasgram.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE mobile = :mobile LIMIT 1")
    suspend fun getUserByMobile(mobile: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE (senderMobile = :mobile1 AND receiverMobile = :mobile2) OR (senderMobile = :mobile2 AND receiverMobile = :mobile1) ORDER BY timestamp ASC")
    fun getMessagesBetween(mobile1: String, mobile2: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE receiverMobile = :groupId ORDER BY timestamp ASC")
    fun getGroupMessages(groupId: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<Message>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups")
    fun getAllGroups(): Flow<List<Group>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<Group>)
}

@Dao
interface StatusDao {
    @Query("SELECT * FROM statuses")
    fun getAllStatuses(): Flow<List<Status>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatuses(statuses: List<Status>)
}
