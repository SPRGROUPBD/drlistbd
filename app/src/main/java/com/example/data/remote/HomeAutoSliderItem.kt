package com.example.data.remote

import com.example.i18n.AppLang
import com.example.ui.ads.ResolvedHomeSliderSlide

enum class SliderQuickFilter { ALL, TODAY, SCHEDULE, HOSPITAL, DIAGNOSTIC, NOTICE }

data class HomeAutoSliderItem(
    val id: String,
    val category: String = "NOTICE",
    val titleBn: String = "",
    val titleEn: String = "",
    val subtitleBn: String = "",
    val subtitleEn: String = "",
    val descriptionBn: String = "",
    val descriptionEn: String = "",
    val specialtyBn: String = "",
    val specialtyEn: String = "",
    val designationBn: String = "",
    val designationEn: String = "",
    val addressBn: String = "",
    val addressEn: String = "",
    val imageUrl: String = "",
    val targetRecordType: String = "",
    val targetRecordId: String = "",
    val destinationUrl: String = "",
    val priority: Int = 0
) {
    fun displayName(lang: AppLang): String =
        localized(titleBn, titleEn, lang).ifBlank { localized(subtitleBn, subtitleEn, lang) }

    fun displaySpecialty(lang: AppLang): String =
        localized(specialtyBn, specialtyEn, lang)

    fun displayDesignation(lang: AppLang): String =
        localized(designationBn, designationEn, lang)

    fun displayAddress(lang: AppLang): String =
        localized(addressBn, addressEn, lang)

    fun displaySubtitle(lang: AppLang): String =
        localized(subtitleBn, subtitleEn, lang)

    fun displayDescription(lang: AppLang): String =
        localized(descriptionBn, descriptionEn, lang)

    private fun localized(bn: String, en: String, lang: AppLang): String =
        if (lang == AppLang.BN) bn.ifBlank { en } else en.ifBlank { bn }

    companion object {
        fun fromResolvedSlide(slide: ResolvedHomeSliderSlide, index: Int) = HomeAutoSliderItem(
            id = "resolved_${slide.slideId}_$index",
            category = slide.categoryBadge.ifBlank { slide.targetRecordType }.uppercase(),
            titleBn = slide.title,
            titleEn = slide.title,
            subtitleBn = slide.subtitle,
            subtitleEn = slide.subtitle,
            descriptionBn = slide.description,
            descriptionEn = slide.description,
            imageUrl = slide.imageUrl,
            targetRecordType = slide.targetRecordType.uppercase(),
            targetRecordId = slide.targetRecordId,
            destinationUrl = slide.destinationUrl,
            priority = index
        )
    }
}
