package com.rebuild.ui

import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.PaddingValues
import android.content.res.Configuration
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.rebuild.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.annotation.DrawableRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun compactLandscapeInput(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE && WindowInsets.isImeVisible

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    compactWhenKeyboard: Boolean = false,
    /** A single icon action stays beside the title even at large text instead of taking a row of its own. */
    iconOnlyActions: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val compactLandscape = compactWhenKeyboard && compactLandscapeInput()
    Column(modifier.fillMaxSize()) {
        // The header lines up with the centered, width-capped body on wide windows.
        if (!compactLandscape) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { Column(
            Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        RebuildIcon(R.drawable.ic_arrow_left, contentDescription = "Back")
                    }
                }
                // A top-level destination names itself large; a pushed screen keeps a compact title beside Back.
                Text(
                    title,
                    style = if (onBack == null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = if (onBack == null) 8.dp else 0.dp)
                        .semantics { heading() }
                )
                if (iconOnlyActions || LocalDensity.current.fontScale <= 1.3f) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                        content = actions)
                }
            }
            if (!iconOnlyActions && LocalDensity.current.fontScale > 1.3f &&
                (!compactWhenKeyboard || !WindowInsets.isImeVisible)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            if (subtitle != null && (!compactWhenKeyboard || !WindowInsets.isImeVisible)) Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp).testTag("screen-subtitle")
            )
        } }
        content()
    }
}

/**
 * Keeps a scrolling body beneath the fixed controls stacked above it in the same Column. Compose stretches the
 * accessibility bounds of a row partly scrolled under the body's top edge up to 24dp past that edge, and the later
 * sibling claims its bounds first, so the row would take the lower part of a chip or pill above from the platform's
 * node bounds: TalkBack, Switch Access and accessibility checks would see a cut target. Column siblings never
 * overlap, so nothing visible or tappable changes. Not for Box children, where it would also draw under them.
 */
internal fun Modifier.yieldsToFixedControls(): Modifier = zIndex(-1f)

/** Readable line length on wide windows: content stays a phone-width column, centered. */
internal val MAX_CONTENT_WIDTH = 640.dp

/** A full-width body capped at [MAX_CONTENT_WIDTH] and centered, for lists, docks and rails on wide windows. */
internal fun Modifier.readableWidth(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth()

/** Scrollable screen body with standard padding, capped at a readable width. */
@Composable
internal fun ColumnScope.ScrollBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .yieldsToFixedControls()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier
                .widthIn(max = MAX_CONTENT_WIDTH)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 8.dp).semantics { heading() }
    )
}

@Composable
internal fun SecondaryTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        // Starts on the content edge, so a list of these lines up with the text above it.
        contentPadding = PaddingValues(start = 0.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
    ) {
        if (icon != null) {
            RebuildIcon(icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        content()
    }
}

@Composable
internal fun ErrorText(message: String?) {
    if (message != null) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite; error(message) })
    }
}

/**
 * Back closes the keyboard before the dialog or sheet around [content]: while a field inside has focus and the
 * keyboard is up, back only hides it. Some keyboards pass back straight to a dialog, which would close with the
 * typing in it. With the keyboard already down, back clears the focus and calls [onBack].
 */
@Composable
internal fun KeyboardFirstBack(onBack: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    BackHandler(enabled = focused) {
        val keyboardUp = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        focusManager.clearFocus()
        if (!keyboardUp) onBack()
    }
    Box(modifier.onFocusChanged { focused = it.hasFocus }) { content() }
}

/** Scrollable content and wrapping actions also fit enlarged text and a visible IME. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdaptiveDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = {
            KeyboardFirstBack(onDismissRequest) {
                Column(Modifier.verticalScroll(rememberScrollState()).testTag("dialog-body"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) { text() }
            }
        },
        // Material order, dismiss then confirm at the end; they wrap onto a second line only when they don't fit.
        confirmButton = {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                dismissButton()
                confirmButton()
            }
        }
    )
}

@Composable
internal fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    busy: Boolean = false,
    error: String? = null
) {
    AdaptiveDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text)
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !busy,
                modifier = Modifier.testTag("confirm-button"),
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else ButtonDefaults.textButtonColors()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("dialog-cancel")) { Text("Cancel") } }
    )
}

@Composable
internal fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    filterNumericInput: Boolean = true,
    // Lets a compact row show one shared message below the fields while each field still exposes its own error.
    errorSemantics: String? = supportingText
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(if (filterNumericInput)
            new.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) } else new) },
        label = { Text(label, maxLines = 2) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.secondary,
            focusedLabelColor = MaterialTheme.colorScheme.secondary
        ),
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = if (isError && errorSemantics != null) modifier.semantics { error(errorSemantics) } else modifier
    )
}

@Composable
internal fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedIconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
            RebuildIcon(R.drawable.ic_minus, contentDescription = "Fewer $label")
        }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .widthIn(min = 32.dp)
                .semantics { stateDescription = "$value" }
        )
        OutlinedIconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
            RebuildIcon(R.drawable.ic_plus, contentDescription = "More $label")
        }
    }
}

@Composable
internal fun CheckRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 12.dp),
        // The box sits on the first line, so a long label never floats it to the middle of a paragraph.
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Column(Modifier.padding(start = 12.dp)) {
            Text(text)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun RadioRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.padding(start = 12.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Visible rest-alert limitations with remedies; purely informational, never gates logging. */
@Composable
internal fun PlatformLimitations(status: PlatformStatus, platform: PlatformActions, showAllClear: Boolean = false) {
    val issues = buildList<Triple<String, String, () -> Unit>> {
        if (!status.notificationsAllowed) add(
            Triple("Notifications are off, so rest alerts can't appear.", "Allow notifications") { platform.requestNotifications() }
        )
        if (!status.exactAlarmsAllowed) add(
            Triple("Exact alarms are off, so a rest alert may arrive late.", "Allow exact alarms") { platform.requestExactAlarms() }
        )
        if (status.batteryRestricted) add(
            Triple("Battery restrictions may delay rest alerts.", "Battery settings") { platform.openBatterySettings() }
        )
    }
    if (issues.isEmpty()) {
        if (showAllClear) Text("Rest alerts can be delivered on time.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        issues.forEach { (text, label, action) ->
            Text(text, style = MaterialTheme.typography.bodyMedium)
            SecondaryTextButton(onClick = action, modifier = Modifier.semantics { contentDescription = label }) { Text(label) }
        }
        Text(
            "You can still log every set; the timer on screen stays accurate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Flat list row aligned with the screen's content padding (no nested card or inset). A [label] replaces the
 * children's text for screen readers, so a row scrolled almost out of a long list still announces its name.
 */
@Composable
internal fun ListRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    label: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (label != null) Modifier.clearAndSetSemantics { contentDescription = label } else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (leading != null) 16.dp else 0.dp)
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f)) {
            Text(headline, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * Round order marker shared by the exercise rail, Today's session and the plan: the exercise's number, or a
 * check once its working-set target is logged. Emphasized marks the exercise the workout opens on or shows.
 * Decorative for screen readers: the surrounding row or heading carries the words.
 */
@Composable
internal fun OrderBadge(number: Int, met: Boolean = false, emphasized: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        met -> colors.tertiaryContainer
        emphasized -> colors.primary
        else -> colors.surfaceContainerHighest
    }
    // Grows with the text so the number never clips at large font sizes.
    val size = with(LocalDensity.current) { maxOf(24.dp, MaterialTheme.typography.labelMedium.lineHeight.toDp() + 6.dp) }
    Box(Modifier.size(size).clip(CircleShape).background(background).clearAndSetSemantics {},
        contentAlignment = Alignment.Center) {
        if (met) {
            RebuildIcon(R.drawable.ic_check, contentDescription = null, tint = colors.onTertiaryContainer,
                modifier = Modifier.size(size * 2 / 3))
        } else {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = if (emphasized) colors.onPrimary else colors.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * Small calendar block: a short weekday over an optional day number. Screen readers hear [description]
 * (the full weekday or date) instead of the abbreviations.
 */
@Composable
internal fun DayBlock(weekday: String, day: String?, description: String, emphasized: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .widthIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (emphasized) colors.secondaryContainer else colors.surfaceContainerHigh)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            weekday.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (emphasized) colors.onSecondaryContainer else colors.onSurfaceVariant,
            maxLines = 1
        )
        if (day != null) Text(
            day,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = if (emphasized) colors.onSecondaryContainer else colors.onSurface,
            maxLines = 1
        )
    }
}

/**
 * Content that replaces something else (a finished target, a receipt) rises and fades in once, in 220 ms. Motion
 * only marks the change of state; Android's animation scale, including Remove animations, applies.
 */
@Composable
internal fun EnterOnce(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        modifier = modifier,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220, easing = EaseOutQuart)) { it / 6 }
    ) { content() }
}
