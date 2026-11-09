package com.example.manybird

//import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.serialization.Serializable
import kotlin.math.abs
import androidx.core.graphics.toColorInt

import com.example.manybird.*


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GestureApp()  // fuck it with NO protection
        }
    }
}



@Serializable  // to do : make reachable from Anywhere. singleton...?
data class GeneralSettings(
    val mainColorHex: String = "FFAABBCC", // used for new members etc etc
    val x: Int = 0,  //
    val y: Int = 0,  // either where you left off OR "home screen"
    val z: Int = 0,
    val rememberLocation: Boolean = true, // return to where you left off
    // val noteTitle: String = "untitled"
)

fun getColorFromHex(hex: String): Color {
    return Color(hex.toColorInt())
}

@Composable  // does this need to be composable?
fun ContentLookup(contentKey: String) {
    when (contentKey) {
        "main" -> MainScreen()
        "settings" -> SettingsScreen()
        "chat" -> ChatScreen()
        "notes" -> NotesScreen()
        "archive" -> ArchiveScreen()
        "channels" -> ChannelsScreen()
        "addMember" -> AddMemberScreen({})
        "graphicDemo" -> GraphicsDemoScreen()
        else -> Text("404")
    }
}

@Composable
fun DynamicScreen(colorHex: String, contentKey: String, backgroundId: Int?) {
    val backgroundColor = getColorFromHex(colorHex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            // INSERT BACGROUND IF THERES BACKGROUDND
    ) {
        // Here we look up the function based on the string from the JSON
        ContentLookup(contentKey)
    }
}


/*  // for later: showing custom field data area
@Composable
fun CustomField(definition: CFDefinition, value: CFValue) {
    when (definition.style) {
        Style.TEXT -> TextField(value.text_val)
        Style.DROPDOWN -> DropdownMenu(selected = value.option_id)
        Style.SLIDER -> Slider(value = value.num_val)
        Style.COLOR -> ColorPicker(color = value.num_val)
    }
}
*/


/* for later: making a little thingy for the Chat
@Composable
fun ArtistAvatar(artist: Artist) {
    Box {
        Image(bitmap = artist.image, contentDescription = "Artist image")
        Icon(Icons.Filled.Check, contentDescription = "Check mark")
    }
}
*/


fun dominantSwipeDirection(offsetX: Float, offsetY: Float, threshold: Float): Pair<Int,Int>? {
    if (abs(offsetX) > abs(offsetY)) {
        if (offsetX > threshold) return Pair(-1, 0)
        if (offsetX < -threshold) return Pair(1, 0)
    } else {
        if (offsetY > threshold) return Pair(0, -1)
        if (offsetY < -threshold) return Pair(0, 1)
    }
    return null // didn't cross threshold → no move
}

fun resolveNavigation(gridX: Int, gridY: Int, delta: Pair<Int,Int>?, screenMap: Map<Pair<Int,Int>, ScreenData>): Pair<Int,Int> {
    if (delta == null) return Pair(gridX, gridY)
    val candidate = Pair(gridX + delta.first, gridY + delta.second)
    return if (screenMap.containsKey(candidate)) candidate else Pair(gridX, gridY)
}

data class ScreenData(
    val name: String,
    val color: Color,
    val content: @Composable () -> Unit
)


@Composable
fun GestureApp() {
    val screenMap = mapOf(
        Pair(-2, 0)  to ScreenData("Channels", Color(0xFF7467F5), {ChannelsScreen()}),
        Pair(-1, 0)  to ScreenData("Chat", Color(0xFF5796EA),  {ChatScreen()}),
        Pair(0, 0)   to ScreenData("Main", Color(0xFFFF639E),  {MainScreen()}),
        Pair(2, 0)   to ScreenData("Notes", Color(0xFF9DE866),  {NotesScreen()}),
        Pair(0, -1)   to ScreenData("GraphicsDemo", Color(0xFF9C27B0),  {GraphicsDemoScreen()}),

        Pair(1, 0)   to ScreenData("AddMember", Color(0xFF88A871), {AddMemberScreen({})}),

        Pair(0, 1)   to ScreenData("Settings", Color(0xFFf4c0bb ), { SettingsScreen()}),
        //Pair(0, -1)  to ScreenData("Archive", Color(0xFFf4bbf0),  {ArchiveScreen()}),

        // slideshow for demo purposes
        Pair(3, 0) to ScreenData("Error", Color(0xFF59BE9E),  { GalleryScreen({}) }),
        Pair(4, 0) to ScreenData("Error",Color(0xFF71A3A8),  { Text("404") }),
        Pair(5, 0) to ScreenData("Error", Color(0xFF7187A8),  { Text("404") }),
        Pair(6, 0) to ScreenData("Error",Color(0xFFA071A8),  { Text("404") }),
        Pair(7, 0) to ScreenData("Error", Color(0xFFA87179),  {  }),

        Pair(10, 10) to ScreenData("Error", Color.Black,  { Text("404") })
    )

    /*                [0,-1]
     *  [-2,0] [-1,0] [0, 0] [1, 0] [2,0] [3,0] [4,0] [5,0] [6,0] [7,0]
     *                [0, 1]        [2,1]
     * */

    var gridX by remember { mutableIntStateOf(0) }
    var gridY by remember { mutableIntStateOf(0) }

    // 2. Temporary storage for the current swipe distance
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val currentCoords = Pair(gridX, gridY)

    AnimatedContent(
        targetState = currentCoords,
        transitionSpec = {
            val (targetX, targetY) = targetState
            val (initialX, initialY) = initialState

            val direction = when {
                targetX > initialX -> AnimatedContentTransitionScope.SlideDirection.Left
                targetX < initialX -> AnimatedContentTransitionScope.SlideDirection.Right
                targetY > initialY -> AnimatedContentTransitionScope.SlideDirection.Up
                else -> AnimatedContentTransitionScope.SlideDirection.Down
            }
            // this is where all animation happens.
            slideIntoContainer(direction) togetherWith slideOutOfContainer(direction)
            // this is very basis, but it tends to break when i do anything interesting with it.
        }
    ) { coords -> val screen = screenMap[coords] ?: return@AnimatedContent // if coords don't exist, do not animate.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(screen.color)
                .pointerInput(Unit) { // Unit means "never restart listener" here.
                    detectDragGestures(
                        onDrag = { change, dragAmount -> // ACCUMULATES CHANGE
                            change.consume() // "don't pass this down to any other functions"
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        },
                        onDragEnd = { // SHOOTS ITS LOGIC HERE
                            val delta = dominantSwipeDirection(offsetX, offsetY, threshold = 150f)
                            val (newX, newY) = resolveNavigation(gridX, gridY, delta, screenMap)
                            gridX = newX
                            gridY = newY
                            offsetX = 0f
                            offsetY = 0f
                        },
                        onDragCancel = {  // when swipe gets interrupted
                            offsetX = 0f
                            offsetY = 0f
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            screen.content()
        }
    }
}

