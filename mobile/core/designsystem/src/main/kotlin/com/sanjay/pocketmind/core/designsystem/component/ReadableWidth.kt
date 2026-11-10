package com.sanjay.pocketmind.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sanjay.pocketmind.core.designsystem.theme.PmSize

/**
 * Caps content at a comfortable reading width and centres it.
 *
 * On a phone this does nothing (the screen is narrower than the cap). On a tablet or an unfolded
 * foldable, text lines of 1,200 dp are hard to read (the eye loses the next line); typography
 * guidance is roughly 45–90 characters per line, which is about 720 dp at body size.
 */
fun Modifier.readableWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = PmSize.readableContentWidth)
