package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AdConfigEntity
import com.example.data.local.LocationEntity
import com.example.data.local.SpecialtyEntity
import com.example.data.model.DayOfWeekBd
import com.example.data.model.DoctorFullProfile
import com.example.data.model.SearchFilterState
import com.example.data.model.SortOption
import com.example.data.seed.SeedData
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.ui.ads.ManagedNativeBannerAd
import com.example.ui.components.DoctorCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SpecialtyStrip
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindDoctorsScreen(
    lang: AppLang,
    adConfig: AdConfigEntity,
    searchState: SearchFilterState,
    specialties: List<SpecialtyEntity>,
    locations: List<LocationEntity>,
    filteredDoctors: List<DoctorFullProfile>,
    visibleCount: Int,
    favoriteIds: Set<String>,
    onUpdateSearch: ((SearchFilterState) -> SearchFilterState) -> Unit,
    onResetFilters: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDoctorClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val pagedDoctors = remember(filteredDoctors, visibleCount) {
        filteredDoctors.take(visibleCount)
    }
    val nativeInterval = adConfig.nativeAdInterval.coerceAtLeast(2)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce)
    ) {
        // Search & Filter Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceWhite)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchState.query,
                    onValueChange = { q -> onUpdateSearch { it.copy(query = q) } },
                    placeholder = { Text(AppStrings.searchHint(lang), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MedicalBlue) },
                    trailingIcon = {
                        if (searchState.query.isNotEmpty()) {
                            IconButton(onClick = { onUpdateSearch { it.copy(query = "") } }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("find_doctors_search_input")
                )

                FilledTonalIconButton(
                    onClick = { showFilterSheet = true },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MedicalBlueLight),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("open_filters_button")
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Filters", tint = MedicalBlueDark)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Quick Pill Filter Row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val isDefault = !searchState.availableTodayOnly &&
                        searchState.specialtyId == null && searchState.district == null
                    FilterChip(
                        selected = isDefault,
                        onClick = onResetFilters,
                        label = { Text(AppStrings.allLabel(lang)) }
                    )
                }
                item {
                    FilterChip(
                        selected = searchState.availableTodayOnly,
                        onClick = { onUpdateSearch { it.copy(availableTodayOnly = !it.availableTodayOnly) } },
                        label = { Text(AppStrings.availableToday(lang)) },
                        leadingIcon = if (searchState.availableTodayOnly) {
                            { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )
                }
                if (searchState.district != null) {
                    item {
                        FilterChip(
                            selected = true,
                            onClick = { onUpdateSearch { it.copy(district = null, upazila = null) } },
                            label = { Text(tr(lang, SeedData.districtBnName(searchState.district), searchState.district)) },
                            trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) }
                        )
                    }
                }
                item {
                    Box {
                        FilterChip(
                            selected = true,
                            onClick = { showSortMenu = true },
                            label = { Text(tr(lang, searchState.sortOption.bnLabel, searchState.sortOption.enLabel)) },
                            leadingIcon = { Icon(Icons.Rounded.SwapVert, null, Modifier.size(16.dp)) }
                        )
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortOption.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(tr(lang, opt.bnLabel, opt.enLabel)) },
                                    onClick = {
                                        onUpdateSearch { it.copy(sortOption = opt) }
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Specialty Icon Strip
        Spacer(Modifier.height(8.dp))
        SpecialtyStrip(
            specialties = specialties,
            selectedId = searchState.specialtyId,
            lang = lang,
            onSelect = { specId -> onUpdateSearch { it.copy(specialtyId = specId) } }
        )

        // Results Count
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tr(
                    lang,
                    "${filteredDoctors.size} জন ডাক্তার পাওয়া গেছে",
                    "${filteredDoctors.size} doctors found"
                ),
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            if (searchState != SearchFilterState()) {
                TextButton(onClick = onResetFilters) {
                    Text(tr(lang, "ফিল্টার রিসেট", "Reset Filters"))
                }
            }
        }

        if (pagedDoctors.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Outlined.SearchOff,
                title = tr(lang, "কোনো ফলাফল পাওয়া যায়নি", "No Doctors Found"),
                subtitle = AppStrings.emptyDoctors(lang),
                actionText = tr(lang, "সব ডাক্তার দেখুন", "Show All Doctors"),
                onAction = onResetFilters
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    ManagedNativeBannerAd(adConfig = adConfig, lang = lang)
                }
                itemsIndexed(pagedDoctors, key = { _, it -> it.doctor.id }) { index, doc ->
                    DoctorCard(
                        item = doc,
                        lang = lang,
                        isFavorite = doc.doctor.id in favoriteIds,
                        onToggleFavorite = { onToggleFavorite(doc.doctor.id) },
                        onClick = { onDoctorClick(doc.doctor.id) }
                    )
                    // Periodic inline Native Banner Ad every N cards
                    if ((index + 1) % nativeInterval == 0) {
                        Spacer(Modifier.height(10.dp))
                        ManagedNativeBannerAd(adConfig = adConfig, lang = lang)
                    }
                }
                if (pagedDoctors.size < filteredDoctors.size) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedButton(onClick = onLoadMore) {
                                Text(tr(lang, "আরো ডাক্তার দেখুন", "Load More Doctors"))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showFilterSheet) {
        AdvancedFilterSheet(
            lang = lang,
            searchState = searchState,
            locations = locations,
            onUpdateSearch = onUpdateSearch,
            onDismiss = { showFilterSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedFilterSheet(
    lang: AppLang,
    searchState: SearchFilterState,
    locations: List<LocationEntity>,
    onUpdateSearch: ((SearchFilterState) -> SearchFilterState) -> Unit,
    onDismiss: () -> Unit
) {
    val divisions = remember { SeedData.bangladeshDivisions.map { it.nameEn } }
    val districts = remember(searchState.division) {
        if (searchState.division == null) {
            SeedData.allDistricts.map { it.nameEn }
        } else {
            SeedData.bangladeshDivisions
                .firstOrNull { it.nameEn == searchState.division }
                ?.districts?.map { it.nameEn }
                ?: SeedData.allDistricts.map { it.nameEn }
        }
    }
    val upazilas = remember(locations, searchState.district) {
        locations.filter { searchState.district == null || it.district.equals(searchState.district, ignoreCase = true) }
            .map { it.upazila }.filter { it.isNotBlank() }.distinct()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    tr(lang, "বিস্তারিত ফিল্টার", "Advanced Search Filters"),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            item {
                OutlinedTextField(
                    value = searchState.subSpecialty,
                    onValueChange = { v -> onUpdateSearch { it.copy(subSpecialty = v) } },
                    label = { Text(tr(lang, "সাব-স্পেশালিটি / দক্ষতা", "Sub-specialty / Expertise")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = searchState.facilityQuery,
                    onValueChange = { v -> onUpdateSearch { it.copy(facilityQuery = v) } },
                    label = { Text(tr(lang, "হাসপাতাল বা ডায়াগনস্টিক সেন্টার", "Hospital or Diagnostic Center")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = searchState.workplaceQuery,
                    onValueChange = { v -> onUpdateSearch { it.copy(workplaceQuery = v) } },
                    label = { Text(tr(lang, "বর্তমান কর্মস্থল / মেডিকেল কলেজ", "Current Workplace / College")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text(tr(lang, "বিভাগ (৮টি বিভাগ)", "Division (8 Divisions)"), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(divisions) { div ->
                        FilterChip(
                            selected = searchState.division == div,
                            onClick = {
                                onUpdateSearch {
                                    it.copy(
                                        division = if (it.division == div) null else div,
                                        district = null,
                                        upazila = null
                                    )
                                }
                            },
                            label = { Text(tr(lang, SeedData.divisionBnName(div), div)) }
                        )
                    }
                }
            }

            item {
                Text(
                    tr(lang, "জেলা (${districts.size}টি জেলা)", "District (${districts.size} Districts)"),
                    style = MaterialTheme.typography.titleSmall
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(districts) { dist ->
                        FilterChip(
                            selected = searchState.district == dist,
                            onClick = {
                                onUpdateSearch {
                                    it.copy(
                                        district = if (it.district == dist) null else dist,
                                        upazila = null
                                    )
                                }
                            },
                            label = { Text(tr(lang, SeedData.districtBnName(dist), dist)) }
                        )
                    }
                }
            }

            item {
                Text(tr(lang, "উপজেলা / এলাকা", "Upazila / Area"), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(upazilas) { upz ->
                        FilterChip(
                            selected = searchState.upazila == upz,
                            onClick = {
                                onUpdateSearch { it.copy(upazila = if (it.upazila == upz) null else upz) }
                            },
                            label = { Text(upz) }
                        )
                    }
                }
            }

            item {
                Text(tr(lang, "সপ্তাহের দিন", "Day of Week"), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DayOfWeekBd.entries) { day ->
                        FilterChip(
                            selected = searchState.weekday == day,
                            onClick = {
                                onUpdateSearch { it.copy(weekday = if (it.weekday == day) null else day) }
                            },
                            label = { Text(tr(lang, day.bnLabel, day.enLabel)) }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onUpdateSearch { SearchFilterState() }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(tr(lang, "মুছুন", "Clear"))
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(tr(lang, "প্রয়োগ করুন", "Apply Filters"))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
