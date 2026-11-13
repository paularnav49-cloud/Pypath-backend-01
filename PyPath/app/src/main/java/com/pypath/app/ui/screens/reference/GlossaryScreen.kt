package com.pypath.app.ui.screens.reference

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pypath.app.data.model.GlossaryTerm
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.GlossarySearch
import com.pypath.app.domain.NodeStatus
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.components.CodeBlock
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.theme.AppTheme
import com.pypath.app.ui.theme.Mono
import kotlinx.coroutines.launch

/** Searchable list of Python terms, each linked to the sub-level that teaches it. */
@Composable
fun GlossaryScreen(
    snapshot: CourseSnapshot,
    onBack: () -> Unit,
    onOpenSubLevel: (String) -> Unit,
) {
    val c = AppTheme.colors
    val terms = snapshot.course.glossary
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf<Int?>(null) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val results = remember(terms, query, level) { GlossarySearch.filter(terms, query, level) }
    val levelsWithTerms = remember(terms) { terms.map { it.level }.distinct().sorted() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            AppTopBar("Glossary", "${terms.size} Python terms", onBack)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(40) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                singleLine = true,
                placeholder = { Text("Search, e.g. list, return, f-string") },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = c.textMuted) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear search", tint = c.textMuted) }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.brand, unfocusedBorderColor = c.border,
                    focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
                    focusedTextColor = c.text, unfocusedTextColor = c.text, cursorColor = c.brand,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("All", selected = level == null) { level = null }
                levelsWithTerms.forEach { n -> Chip("Level $n", selected = level == n) { level = if (level == n) null else n } }
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp, top = 8.dp,
                    bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        if (query.isBlank() && level == null) "All terms, A-Z" else "${results.size} result${if (results.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                    )
                }
                if (results.isEmpty()) {
                    item {
                        AppCard(Modifier.fillMaxWidth()) {
                            Text("No terms match \"${query.trim()}\"", style = MaterialTheme.typography.titleSmall, color = c.text)
                            Text("Try a shorter word, or choose All levels.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                        }
                    }
                }
                items(results, key = { it.term }) { t ->
                    val sub = snapshot.subLevel(t.subLevelId)
                    TermCard(
                        t, code = sub?.subLevel?.code, learned = sub?.status == NodeStatus.COMPLETED,
                        open = expanded == t.term,
                        onToggle = { expanded = if (expanded == t.term) null else t.term },
                        onOpenLesson = {
                            if (sub != null && sub.isPlayable) onOpenSubLevel(t.subLevelId)
                            else scope.launch { snackbar.showSnackbar("You'll meet this in Level ${t.level}. Keep going!") }
                        },
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun TermCard(
    t: GlossaryTerm, code: String?, learned: Boolean, open: Boolean,
    onToggle: () -> Unit, onOpenLesson: () -> Unit,
) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), onClick = onToggle, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                t.term, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Mono),
                color = c.text, modifier = Modifier.weight(1f),
            )
            Pill("Level ${t.level}", if (learned) c.success else c.brand, if (learned) c.successSoft else c.brandSoft)
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                if (open) "Collapse" else "Expand", tint = c.textFaint,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(t.definition, style = MaterialTheme.typography.bodyMedium, color = c.text)
        AnimatedVisibility(open) {
            Column {
                t.example?.let {
                    Spacer(Modifier.height(10.dp))
                    CodeBlock(it, output = t.output, title = "example.py")
                }
                if (t.aliases.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Also: ${t.aliases.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onOpenLesson, modifier = Modifier.heightIn(min = 40.dp)) {
                    Text(if (code != null) "Open lesson $code" else "Open lesson", color = c.brand)
                }
            }
        }
    }
}

@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) c.brand else c.surfaceAlt)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .heightIn(min = 48.dp) // Batch 3 Part 3: 48dp touch target (was 36dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) c.onBrand else c.text)
    }
}
