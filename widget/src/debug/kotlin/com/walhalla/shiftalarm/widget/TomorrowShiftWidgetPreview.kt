package com.walhalla.shiftalarm.widget

import androidx.compose.runtime.Composable
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 220, heightDp = 80)
@Composable
private fun TomorrowShiftWidgetDayPreview() {
    TomorrowShiftWidgetContent(label = "завтра: день")
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 220, heightDp = 80)
@Composable
private fun TomorrowShiftWidgetOffPreview() {
    TomorrowShiftWidgetContent(label = "завтра: выходной")
}
