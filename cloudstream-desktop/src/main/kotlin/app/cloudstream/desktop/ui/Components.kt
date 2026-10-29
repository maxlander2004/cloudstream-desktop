package app.cloudstream.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.core.legacy.CatalogSection
import coil3.compose.AsyncImage

/**
 * The poster card upstream draws in `home_result_grid.xml`: 114dp × 180dp with rounded
 * corners, the title over a bottom gradient (or below the artwork), and the item's episode
 * label underneath when there is one.
 */
@Composable
internal fun PosterCell(
    item: CatalogItem,
    titleUnder: Boolean,
    onOpen: (CatalogItem) -> Unit,
    width: Dp? = null,
) {
    Column(
        Modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .clickable { onOpen(item) },
    ) {
        Box(
            Modifier.fillMaxWidth()
                // Upstream's poster aspect ratio, 114 × 180.
                .aspectRatio(114f / 180f)
                // Upstream's rounded_image_radius is 10dp.
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!titleUnder) {
                // Upstream's title_shadow: the title sits on a bottom gradient over the artwork.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            ),
                        ),
                )
                // Upstream stacks the episode label above the title inside the poster
                // (`episode_text` over `imageText`), both centered on the bottom shadow.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 5.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item.subtitle?.takeIf { it.isNotBlank() }?.let { episode ->
                        Text(
                            episode,
                            color = Color.White,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                    Text(
                        item.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
        if (titleUnder) {
            Spacer(Modifier.height(4.dp))
            Text(
                item.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
        }
        // With titles below the poster the episode label sits below the title; otherwise it
        // is drawn inside the artwork above the title, where upstream puts it.
        if (titleUnder) {
            item.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/** *Poster size* maps to a column count the way the original's slider does. */
internal fun gridColumns(posterSize: Int): Int = (8 - posterSize).coerceIn(3, 8)

/** The original's poster grid, used by Search, Library and providers with a flat list. */
@Composable
internal fun PosterGrid(
    items: List<CatalogItem>,
    posterSize: Int,
    titleUnder: Boolean,
    onOpen: (CatalogItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns(posterSize)),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(items, key = { it.url + it.title }) { item ->
            PosterCell(item = item, titleUnder = titleUnder, onOpen = onOpen)
        }
    }
}

/**
 * Advanced search results: one grid, with each provider's name spanning a full row above
 * its own results. A grid nested inside the search list would be measured with an unbounded
 * height and crash, so everything shares this single lazy grid instead.
 */
@Composable
internal fun GroupedResults(
    groups: List<Pair<String, List<CatalogItem>>>,
    posterSize: Int,
    titleUnder: Boolean,
    onOpen: (CatalogItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns(posterSize)),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        groups.forEach { (sourceName, group) ->
            if (group.isEmpty()) return@forEach
            item(key = "header:$sourceName", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    sourceName,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
            }
            items(group, key = { "$sourceName:${it.url}:${it.title}" }) { item ->
                PosterCell(item = item, titleUnder = titleUnder, onOpen = onOpen)
            }
        }
    }
}

/**
 * The original's home layout: every provider row is a named horizontal rail of posters,
 * matching upstream's `HomePageResponse` items.
 */
@Composable
internal fun PosterRails(
    sections: List<CatalogSection>,
    titleUnder: Boolean,
    onOpen: (CatalogItem) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        sections.forEach { section ->
            item(key = "header:${section.name}") {
                RailHeader(section.name)
            }
            item(key = "row:${section.name}") {
                PosterRailRow(section.items, titleUnder, onOpen)
            }
        }
    }
}

/** One named row of posters, as upstream's `homepage_parent.xml` draws it. */
@Composable
internal fun PosterRailRow(
    items: List<CatalogItem>,
    titleUnder: Boolean,
    onOpen: (CatalogItem) -> Unit,
) {
    // Upstream's rail uses a 5dp horizontal padding and 2dp card margins.
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(items, key = { "${it.url}:${it.title}:${it.subtitle.orEmpty()}" }) { item ->
            PosterCell(
                item = item,
                titleUnder = titleUnder,
                onOpen = onOpen,
                width = HomePosterWidth,
            )
        }
    }
}

/** A rail's name with the arrow the original puts on the right. */
@Composable
internal fun RailHeader(name: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Upstream's WatchHeaderText: 18sp, bold, with the arrow on the right.
        Text(
            name,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Upstream's poster cell is 114dp × 180dp. */
internal val HomePosterWidth = 114.dp
