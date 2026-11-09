package com.example.manybird

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

import com.example.manybird.*
import com.example.manybird.R
import androidx.core.graphics.toColorInt

@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val viewModel: GraphicsViewModel = appViewModel()
    val graphics by viewModel.allGraphics.collectAsStateWithLifecycle()

    Column(modifier = Modifier.Companion.fillMaxSize()) {
        Button(onClick = onBack) { Text("Back") }  // back button

        LazyVerticalGrid(  // lazy --> scrolls
            columns = GridCells.Fixed(3),
            modifier = Modifier.Companion.fillMaxSize()
        ) {
            items(graphics) { graphic ->
                AsyncImage(
                    model = graphic.uri,
                    contentDescription = graphic.name,
                    modifier = Modifier.Companion
                        .aspectRatio(1f)
                        .fillMaxWidth(),
                    contentScale = ContentScale.Companion.Inside
                )
                //Text(graphic.id.toString())

            }
        }
    }
}

@Composable
fun GraphicsDemoScreen() {
    val viewModel: GraphicsViewModel = appViewModel()

    val launcher = rememberLauncherForActivityResult(  // this is android inbuilt photo picker
        ActivityResultContracts.PickVisualMedia()  // "remember" means keep alive between redraws
    ) { uri ->  // the {} is what happens after user finished picking
        uri?.let { viewModel.saveGraphic(it.toString(), "background") }
        // it.toString <-- "it" being the uri we just got
    }

    val backgroundUri by viewModel.backgroundUri.collectAsStateWithLifecycle()
    var showGallery by remember { mutableStateOf(false) }

    Box(modifier = Modifier.Companion.fillMaxSize()) {
        if (backgroundUri != null) {
            AsyncImage(
                model = backgroundUri,
                contentDescription = null,
                contentScale = ContentScale.Companion.Crop,
                modifier = Modifier.Companion.fillMaxSize()
            )
        }
        if (showGallery) {
            GalleryScreen(
                onBack = { showGallery = false }
            )
        } else {
            Box(modifier = Modifier.Companion.fillMaxSize()) {
                if (backgroundUri != null) {
                    AsyncImage(
                        model = backgroundUri,
                        contentDescription = null,
                        modifier = Modifier.Companion.fillMaxSize(),
                        contentScale = ContentScale.Companion.Crop
                    )
                }
                Column(modifier = Modifier.Companion.align(Alignment.Companion.Center)) {
                    Button(
                        onClick = {
                            launcher.launch(  // launches the image picker.
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) { Text("Pick Image") }
                    Button(
                        onClick = { showGallery = true }
                    ) { Text("Show Gallery") }
                }
            }
        }
    }
}


@Composable
fun AddMemberScreen(onDone: () -> Unit) {
    val viewModel: MemberViewModel = appViewModel()

    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.Companion
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") }
        )
        Button(
            onClick = {
                if (name.isNotBlank()) {
                    viewModel.addMember(name)
                    onDone()
                }
            }
        ) { Text("Add Member") }
    }
}

@Composable
fun ChatScreen(){
    Column {
        Text("Message from Fictive #8645", fontSize = 20.sp)
        Text("pick up milk pls", fontSize = 16.sp)
        Text("Message from Evil Alter #995421", fontSize = 20.sp)
        Text("pick up milk or im Killing", fontSize = 16.sp)
    }
    // clip(CircleShape) --> useful for pfp
}

@Composable
fun NotesScreen() {
    Column {
        Text("Note 1")
        Text("Note 2")
        Text("Note 3")
        Text("...")
    }
}

@Composable
fun SettingsScreen() {
    Text("This is where settings go.")
}

@Composable
fun ArchiveScreen() {
    Text("Archive")
    // like main screens but for archived members
}

@Composable
fun ChannelsScreen() {
    Text("This is the Channel Map Screen!")
}



data class MemberNode( // helper for tree structure
    val member: Members,
    val depth: Int // 0 for root, 1 for child, ...
)

// I HAVE NO IDEA if this works - dont have any way to test it rn...
fun buildFlattenedTree(members: List<Members>, parentId: Int? = null, depth: Int = 0): List<MemberNode> {
    return members
        .filter { it.parentId == parentId }  // notice the syntax
        .sortedBy { it.sortOrder }
        .flatMap { member ->
            listOf(MemberNode(member, depth)) + buildFlattenedTree(members, member.id, depth + 1)
            // recursive --> typical for tree structures
        }
}

@Composable
fun MainScreen() {
    val viewModel: MemberViewModel = appViewModel()
    val members by viewModel.allMembers.collectAsStateWithLifecycle()

    // remembered so it does not recalculate for no reason
    val flattenedTree = remember(members) {
        buildFlattenedTree(members)
    }

    LazyColumn(horizontalAlignment = Alignment.Start) { // TWEAK THE POSITION OF THIS so its less annoying
        item { Text("MEMBERS HERE:", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp)) }
        items(flattenedTree, key = { it.member.id }) { node ->
            // Pass the depth to the card for indentation!
            MemberCard(
                name = node.member.name,
                note = node.member.note,
                modifier = Modifier.padding(start = (node.depth * 24).dp) // The magic indentation
            )
        }
    }
}


fun getTextColor(backgroundColor: Color): Color {
    // luminance() returns a value between 0.0 (darkest) and 1.0 (brightest)
    val luminance = backgroundColor.luminance()

    return if (luminance > 0.5) {
        Color.Black // Light background, use dark text
    } else {
        Color.White // Dark background, use light text
    }
}

@Composable
fun MemberCard(
    modifier: Modifier = Modifier,
    name: String = "Unnamed",
    note: String = "",
    colorHex: String = "#FFAABBCC",
    profilePicture: Painter = painterResource(id = R.drawable.icongrey)
) {
    val backgroundColor = Color(colorHex.toColorInt())
    val textColor = getTextColor(backgroundColor)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp, 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Picture
            Image(
                painter = profilePicture,
                contentDescription = "Profile picture of $name",
                modifier = Modifier
                    .size(50.dp)
                    //.clip(CircleShape)
                    //.border(1.5.dp, Color.White, CircleShape)
                ,
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Text Information
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                if (note.isNotEmpty()) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Preview(showBackground=true)
@Composable
fun MemberCardPreview() {
    MemberCard(name="Jane Doe", note="The bestest girl!", colorHex = "#FF112233")
}


