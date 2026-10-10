package three.haddad.smartnotas.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shoulders",
    indices = [Index(value = ["normalizedName"], unique = true)],
)
data class ShoulderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val colorHex: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
