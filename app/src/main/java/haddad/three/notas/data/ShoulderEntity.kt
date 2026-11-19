package haddad.three.notas.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "shoulders",
    indices = [
        Index(value = ["normalizedName"], unique = true),
        Index(value = ["uuid"], unique = true),
        Index(value = ["ownerId"]),
    ],
)
data class ShoulderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val ownerId: String? = null,
    val name: String,
    val normalizedName: String,
    val colorHex: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
