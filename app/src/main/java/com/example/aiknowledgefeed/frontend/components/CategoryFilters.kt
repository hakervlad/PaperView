package com.example.aiknowledgefeed.frontend.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aiknowledgefeed.frontend.demo.DemoCategory

@Composable
fun CategoryFilters(
    selected: DemoCategory?,
    onSelect: (DemoCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) },
                label = { Text("Alle Themen", Modifier.padding(vertical = 5.dp)) })
        }
        items(DemoCategory.entries, key = { it.name }) { category ->
            FilterChip(selected = selected == category, onClick = { onSelect(category) },
                label = { Text(category.label, Modifier.padding(vertical = 5.dp)) })
        }
    }
}
