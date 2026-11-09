package com.example.manybird

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Delete
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.ForeignKey.Companion.CASCADE
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@SuppressLint("RestrictedApi")  /// fix this by waiting until room3 stops being cringe
@Database(entities = [  // IF YOU ADD A TABLE DONT FORGET TO ADD IT HERE TOO
    Graphics::class, Notes::class,
    Screens::class,
    Channels::class,  Messages::class,
    FrontLogs::class, Members::class,
    CFDef::class,  CFMeta::class,  CFAnswer::class,
], version = 3) // DONT FORGET TO CHANGE VERSION WHEN YOU CHANGE SCHEME ELSE APP EXPLODES
abstract class AppDatabase : RoomDatabase() {
    abstract fun graphicsDao(): GraphicsDao
    abstract fun membersDao(): MembersDao
    abstract fun screenDao(): ScreenDao
    abstract fun textDao(): TextDao
    abstract fun frontDao(): FrontDao
    abstract fun customFieldDao(): CustomFieldDao
    // IF YOU ADD A DAO DONT FORGET TO ADD IT HERE

    companion object {  // means that its singleton
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            // no matter how many times w call this, it builds only once.
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context, AppDatabase::class.java, "app-database")
                    .setDriver(BundledSQLiteDriver())
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
            }
        }
    }
}

@Entity(
    tableName = "screens",
    foreignKeys = [
        ForeignKey(entity = Graphics::class, parentColumns = ["id"], childColumns = ["background"], onDelete = ForeignKey.SET_NULL)
    ],
    primaryKeys = ["x", "y", "z"]
)
data class Screens(
    val x: Int,
    val y: Int,
    val z: Int = 0,  // ignore for now - later implementation stuff
    val colorHex: String,  //  replace by Base Color Of from setting
    val background: Int? = null,
    val contentKey: String  //  check ContentLookup()
)

@Entity(tableName = "graphics")
data class Graphics(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,  // this is actually a Long by kotlin's logic, apparently
    val uri: String,  // "uniform resource identifier"
    val name: String
)


@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(entity = Channels::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.SET_NULL)
    ]
)
data class Channels(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val parentId: Int? = null,
    val name: String,
    val sortOrder: Int = 10
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(entity = Members::class, parentColumns = ["id"], childColumns = ["sender"]), // on delete set 0
        ForeignKey(entity = Channels::class, parentColumns = ["id"], childColumns = ["channel"]), // on delete...????

    ]
)
data class Messages(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: Int,
    val channel: Int,
    val body: String,
    val created: Long,
    val edited: Long? = null
    )

@Entity(
    tableName = "logs",
    foreignKeys = [
        ForeignKey(entity = Members::class, parentColumns = ["id"], childColumns = ["member"]) // on delete set 0
    ]
)
data class FrontLogs(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val member: Int,
    val start: Int,
    val end: Int? = null
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(entity = Notes::class, parentColumns = ["id"], childColumns = ["parentId"])  // on delete set 0
    ]
)
data class Notes (
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val parentId: Int? = null,
    val title: String? = "untitled",  // would there be a way to change this in settings?
    val body: String?,
    val created: Int,
    val edited: Int? = null
)

@Entity(
    tableName = "cf_definitions"
)
data class CFDef (
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: Int,
    val style: Int
)

@Entity(
    tableName = "cf_metadata",
    foreignKeys = [
        ForeignKey(entity = CFDef::class, parentColumns = ["id"], childColumns = ["definitionCF"], onDelete = CASCADE) // is CASCADE ok??
    ],
    primaryKeys = ["definitionCF", "index"]
)
data class CFMeta (
    val definitionCF: Int,
    val index: Int,
    val sortOrder: Int? = 10
)

@Entity(
    tableName = "cf_answers",
    foreignKeys = [
        ForeignKey(entity = Members::class, parentColumns = ["id"], childColumns = ["memberId"], onDelete = CASCADE),
        // dont have to keep it if the member got deleted --> cascade
        ForeignKey(entity = CFDef::class, parentColumns = ["id"], childColumns = ["definitionId"], onDelete = CASCADE),
        ForeignKey(
            entity = CFMeta::class,
            parentColumns = ["definitionCF", "index"],
            childColumns = ["definitionId", "optionVal"]
        )
    ],
    primaryKeys = ["memberId", "definitionId", "index"]
)
data class CFAnswer (
    val memberId: Int,
    val definitionId: Int,
    val index: Int,
    val textVal: String? = null,
    val numVal: Int? = null,
    val optionVal: Int? = null
)


// member with id 0 kinda makes up the "base profile" of the user.
// you should be able to get to it and change it through setting screen.
@Entity(
    tableName = "members",
    foreignKeys = [
        ForeignKey(entity = Members::class, parentColumns = ["id"], childColumns = ["parentId"]), // on delete 0
        ForeignKey(
            entity = Graphics::class,
            parentColumns = ["id"],
            childColumns = ["profilePicture"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Graphics::class,
            parentColumns = ["id"],
            childColumns = ["banner"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Members(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val parentId: Int? = null,
    val name: String,
    val desc: String? = null,
    val note: String = "",    // footnote (like pronouns)
    val profilePicture: Int? = null,
    val banner: Int? = null,
    val sortOrder: Int? = 10,
    val archived: Long? = null
)





@Dao
interface MembersDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(members: Members): Long

    @Query("SELECT * FROM members WHERE archived is NULL")
    fun getAllMembers(): Flow<List<Members>>  // not suspend func to update automatically

    @Query("SELECT * FROM members WHERE archived is not NULL")
    fun getAllArchivedMembers(): Flow<List<Members>>

    @Query("UPDATE members SET name=:name WHERE id=:id")
    suspend fun updateMemberName(id: Int, name: String)

    @Query("UPDATE members SET desc=:desc WHERE id=:id")
    suspend fun updateMemberDesc(id: Int, desc: String)

    @Query("UPDATE members SET profilePicture=:idGraphic WHERE id=:idMember")
    suspend fun updateMemberAvatar(idMember: Int, idGraphic: Int)

    @Query("UPDATE members SET banner=:idGraphic WHERE id=:idMember")
    suspend fun updateMemberBanner(idMember: Int, idGraphic: Int)

    @Query("UPDATE members SET sortOrder=:order WHERE id=:id")
    suspend fun updateMemberSort(order: Int, id: Int)

    @Query("UPDATE members SET parentId=:parent WHERE id=:id")
    suspend fun updateMemberParent(parent: Int, id: Int)

    @Query("UPDATE members SET archived=:time WHERE id=:id")
    suspend fun archiveMember(time:Long, id:Int)
    // time from viewmodel as system.currentTime

    @Query("UPDATE members SET archived=NULL WHERE id=:id")
    suspend fun unarchiveMember(id:Int)

    @Delete
    suspend fun deleteMember(members: Members)  // PASS THE ENTIRE OBJECT
    // everyone who had them as a parent gets new parent 0
}


@Dao
interface GraphicsDao {
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)  // means it replaces row if same primary key (is this necessary?)
    suspend fun insert(graphics: Graphics): Long  // returns the id of graphics it made

    @Query("SELECT * FROM graphics WHERE id = :id")
    suspend fun getById(id: Int): Graphics?   // returns and entire Graphics Object

    @Query("SELECT * FROM graphics")
    fun getAllGraphics(): Flow<List<Graphics>>  // not suspend func to update automatically
}

@Dao
interface ScreenDao {
    //@Query("SELECT * FROM screens WHERE z = 0")
    //fun getAllScreens()

    // @Query("")
    //fun updateBackground(x: Int, y: Int, z: Int? = 0, uri: String)
    // to do : not sure how to make it first upload graphics and then use the generated id.
    // i guess it should be sequenced in the viewmodel...

    //@Query("UPDATE screens SET x=nx, y=ny WHERE y=y and x=x and z=0")
    //fun updateCoords(x: Int, y: Int, nx: Int, ny: Int)  // CHECK SOMEWHERE ELSE IF YOU ARENT OVERWRITING ANYTHING
}

@Dao
interface TextDao {
    //@Query("INSERT INTO messages VALUES :sender, :channel, :body, :time") // sql validity?
    //fun sendMessage(sender: Int, channel: Int, body: String, time: Long)
    // TIME IS PASSED FROM VIEWMODEL AS System.currentTimeMillis()

    //@Query("INSERT INTO messages(body) VALUES :body WHERE id=:id") // sql validity?
    //fun editMessage (id: Int, newBody: String)
}

@Dao
interface FrontDao {
    // start log
    // finish log
    // create log from scratch
}

@Dao
interface CustomFieldDao {
    // create custom
    // create meta (for type option)
    // delete custom
    // create answer type text
    // create answer type number
    // create answer type option
    // this will be a mess...
}










class MemberViewModel(private val dao: MembersDao) : ViewModel() {


    val allMembers: StateFlow<List<Members>> = dao.getAllMembers()
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5000), emptyList())


    fun addMember(name: String) = viewModelScope.launch {
        dao.insert(Members(name = name))
    }

    //val archivedMembers: StateFlow<List<Members>>  // to finish when i need it
}

class GraphicsViewModel(private val dao: GraphicsDao) : ViewModel() {
    private val _backgroundUri = MutableStateFlow<String?>(null)
    val backgroundUri: StateFlow<String?> = _backgroundUri.asStateFlow() // fix when screen backgrounds work

    val allGraphics: StateFlow<List<Graphics>> = dao.getAllGraphics()  // this is a get gallery functionality
        .stateIn(
            viewModelScope,
            SharingStarted.Companion.WhileSubscribed(5000),
            emptyList()
        )

    fun saveGraphic(uri: String, name: String) = viewModelScope.launch {

        val lastInsertedID: StateFlow<Long?>  // this prolly needs wiring in.

        // launch -> because we dc about what happens here.
        dao.insert(Graphics(uri = uri, name = name))
        _backgroundUri.value = uri  // fix this. this sucks
    }

    suspend fun getGraphic(id: Int): Graphics? {
        return dao.getById(id)
    }


}





@Composable
inline fun <reified VM : ViewModel> appViewModel(): VM {
    val context = LocalContext.current
    return viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getInstance(context)
            return when (VM::class) {
                MemberViewModel::class -> MemberViewModel(db.membersDao())
                GraphicsViewModel::class -> GraphicsViewModel(db.graphicsDao())
                // DO NOT FORGET TO ADD VIEWS IN HERE
                else -> error("Unknown ViewModel: ${VM::class}")
            } as T
        }
    })
}