package com.rebuild.ui

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.composed
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.foundation.border
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.FilledTonalButton
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
import com.rebuild.ui.theme.SignalAmber
import com.rebuild.ui.theme.InkPress
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
                .padding(start = if (onBack == null) 0.dp else 0.dp)
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
                    style = if (onBack == null) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = if (onBack == null) 12.dp else 8.dp, end = 8.dp,
                        top = if (onBack == null) 16.dp else 0.dp, bottom = if (onBack == null) 4.dp else 0.dp)
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
                subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 4.dp).testTag("screen-subtitle")
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
    val scroll = rememberScrollState()
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .yieldsToFixedControls()
            .fadingEdges(scroll, if (LocalDensity.current.fontScale > 1.3f) 48.dp else 32.dp)
            .verticalScroll(scroll),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier
                .widthIn(max = MAX_CONTENT_WIDTH)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 12.dp).semantics { heading() }
    )
}

@Composable
internal fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    content: @Composable RowScope.() -> Unit
) {
    // Every action looks like something to press: a quiet tonal container, below the filled amber primary. Plain
    // text buttons are kept for dialog actions only.
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.padding(vertical = 4.dp).heightIn(min = 48.dp),
        enabled = enabled,
        contentPadding = PaddingValues(start = if (icon != null) 12.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = tonalFill(),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        if (icon != null) {
            RebuildIcon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
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
    dismissButton: @Composable () -> Unit = {},
    /** An action that is neither confirm nor dismiss (such as Clear), set apart at the start of the button row. */
    neutralButton: (@Composable () -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = {
            CompositionLocalProvider(LocalDialogSlot provides DialogSlot.BODY) { KeyboardFirstBack(onDismissRequest) {
                Column(Modifier.verticalScroll(rememberScrollState()).testTag("dialog-body"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) { text() }
            }
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
                if (neutralButton != null) {
                    Box(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalDialogSlot provides DialogSlot.DISMISS) { neutralButton() }
                    }
                }
                CompositionLocalProvider(LocalDialogSlot provides DialogSlot.DISMISS) { dismissButton() }
                CompositionLocalProvider(LocalDialogSlot provides DialogSlot.CONFIRM) { confirmButton() }
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
    errorSemantics: String? = supportingText,
    /** Shown inside the empty field once it's focused, e.g. the target range. */
    placeholder: String? = null,
    /** The value's type: the set entry passes a large numeral style. */
    valueStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
    /** A numeric tile (live set entry): label always on top, the value large beneath, an em dash when empty. */
    tile: Boolean = false
) {
    if (tile) {
        NumberTile(value, onValueChange, label, modifier, decimal, isError, supportingText, imeAction, onDone,
            filterNumericInput, errorSemantics, valueStyle)
        return
    }
    // A filled field with a hairline edge: tone and edge show where to type before it's touched (even on a dialog,
    // whose surface matches the usual fill), and focus draws a 2 dp ring.
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val accentRing = accentText()
    val fill = tonalFill()
    val ring = when {
        isError -> colors.error
        focused -> accentRing
        else -> idleFieldEdge()
    }
    val ringWidth = if (isError || focused) 2.dp else 1.dp
    TextField(
        // A hint, never mistaken for a value: lighter and regular weight.
        placeholder = placeholder?.let { { Text(it, maxLines = 1, style = valueStyle.copy(fontWeight = FontWeight.Normal),
            color = colors.onSurfaceVariant.copy(alpha = 0.6f)) } },
        value = value,
        onValueChange = { new -> onValueChange(if (filterNumericInput)
            new.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) } else new) },
        label = { Text(label, maxLines = 2) },
        singleLine = true,
        textStyle = valueStyle,
        shape = MaterialTheme.shapes.small,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerLowest,
            unfocusedContainerColor = fill,
            errorContainerColor = fill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedLabelColor = colors.onSurfaceVariant,
            unfocusedLabelColor = colors.onSurfaceVariant
        ),
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = (if (isError && errorSemantics != null) modifier.semantics { error(errorSemantics) } else modifier)
            .onFocusChanged { focused = it.isFocused }
            .border(ringWidth, ring, MaterialTheme.shapes.small)
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // One card per limitation: what's wrong, and the remedy beside it.
        issues.forEachIndexed { i, (text, label, action) ->
            Column(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, groupShape(i, issues.lastIndex)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                SecondaryButton(onClick = action, modifier = Modifier.semantics { contentDescription = label }) { Text(label) }
            }
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
        emphasized -> SignalAmber // a small marker, not a large fill: full signal amber in both themes
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
                color = if (emphasized) colors.onPrimaryContainer else colors.onSurfaceVariant,
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

/** A card: a lighter (light theme) or raised (dark theme) tonal container, never outlined. */
@Composable
internal fun TonalCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium,
    content: @Composable () -> Unit
) {
    // A plain clipped background, not a Surface: a card is no accessibility node of its own.
    Box(modifier.fillMaxWidth().clip(shape).background(color)) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides contentColorFor(color)) { content() }
    }
}

/** Where a session stands, as one segment per exercise: done, current or still to do. */
internal enum class TrackState { DONE, CURRENT, PENDING }

/**
 * A segmented bar, one segment per exercise. Done segments are green, the current one is signal amber, the rest
 * neutral; [description] carries the same facts for screen readers, so colour is never the only signal.
 */
@Composable
internal fun SessionTrack(states: List<TrackState>, description: String, modifier: Modifier = Modifier) {
    if (states.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(6.dp).clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        states.forEach { state ->
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(
                    when (state) {
                        TrackState.DONE -> colors.tertiary
                        // A mark, not a fill: full signal amber in both themes.
                        TrackState.CURRENT -> SignalAmber
                        TrackState.PENDING -> colors.outlineVariant
                    }
                )
            )
        }
    }
}

/** A number the reader looks for, large and condensed, over a short caption. */
@Composable
internal fun StatTile(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    spoken: String = "$value $caption"
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (emphasized) colors.secondaryContainer else colors.surfaceContainerHigh)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Label on top, value beneath: the same anatomy as the entry tiles.
        Text(caption, style = MaterialTheme.typography.labelMedium, maxLines = 1,
            color = if (emphasized) colors.onSecondaryContainer else colors.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.displaySmall,
            color = if (emphasized) colors.onSecondaryContainer else colors.onSurface, maxLines = 1)
    }
}

/** A grouped list's row shape: 20 dp outer corners on the first and last rows, 4 dp where rows meet. */
internal fun groupShape(index: Int, lastIndex: Int): androidx.compose.ui.graphics.Shape {
    val outer = 20.dp
    val inner = 4.dp
    return androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = if (index == 0) outer else inner, topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == lastIndex) outer else inner, bottomEnd = if (index == lastIndex) outer else inner
    )
}

/**
 * A filter or toggle chip filled with tone instead of outlined: neutral when off, amber-tinted with a check when on.
 */
@Composable
internal fun TonalChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = CircleShape,
        border = null,
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            containerColor = tonalFill(),
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    )
}

/**
 * Content scrolling past the top or bottom of a page fades out over 24 dp instead of being sliced by a hard edge
 * (under a fixed title above, or the floating rest pill below). Drawing only: layout and semantics are unchanged.
 */
internal fun Modifier.fadingEdges(
    scroll: androidx.compose.foundation.ScrollState,
    edge: androidx.compose.ui.unit.Dp = 32.dp
): Modifier = composed {
    // Painted over the content in the page colour, with no offscreen layer, so accessibility still sees every row.
    val page = MaterialTheme.colorScheme.surface
    drawWithContent {
        drawContent()
        val top = if (scroll.canScrollBackward) edge.toPx().coerceAtMost(size.height / 4) else 0f
        val bottom = if (scroll.canScrollForward) edge.toPx().coerceAtMost(size.height / 4) else 0f
        if (top > 0f) drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(page, page.copy(alpha = 0f)), startY = 0f, endY = top), size = size.copy(height = top))
        if (bottom > 0f) drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(page.copy(alpha = 0f), page), startY = size.height - bottom, endY = size.height),
            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - bottom), size = size.copy(height = bottom))
    }
}

/** Small, bold, tracked caps for overlines (status, tile captions); the original case is what screen readers hear. */
@Composable
internal fun overlineStyle() = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)

/**
 * A row of a grouped action list (like system settings): an icon in a tonal circle, the label, and a chevron, on a
 * card tone with 20/4 dp corners from [groupShape]. Reads as one button.
 */
@Composable
internal fun GroupedActionRow(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    index: Int,
    lastIndex: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes trailing: Int = R.drawable.ic_chevron_right,
    /** Spoken name, kept even when the row is scrolled almost out of view and its text is clipped. */
    label: String? = null,
    content: @Composable RowScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(groupShape(index, lastIndex))
            .background(colors.surfaceContainerLowest)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            RebuildIcon(icon, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(18.dp))
        }
        androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
        RebuildIcon(trailing, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun NumberTile(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    decimal: Boolean,
    isError: Boolean,
    supportingText: String?,
    imeAction: ImeAction,
    onDone: () -> Unit,
    filterNumericInput: Boolean,
    errorSemantics: String?,
    valueStyle: TextStyle
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val accentRing = accentText()
    val tileFill = tonalFill()
    val ring = when {
        isError -> colors.error
        focused -> accentRing
        else -> Color.Transparent
    }
    // The caller's modifier (tag, focus, weight) goes on the field itself, so it is the node that takes text.
    run {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = { new -> onValueChange(if (filterNumericInput)
                new.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) } else new) },
            singleLine = true,
            textStyle = valueStyle.copy(color = colors.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(accentRing),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = modifier
                .heightIn(min = 72.dp)
                .onFocusChanged { focused = it.isFocused }
                .semantics {
                    contentDescription = label
                    if (isError && errorSemantics != null) error(errorSemantics)
                },
            decorationBox = { inner ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(if (focused) colors.surfaceContainerLowest else tileFill)
                        .border(2.dp, ring, MaterialTheme.shapes.small)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // The label is always the overline, so empty and filled tiles share one anatomy.
                    Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Box {
                        if (value.isEmpty() && !focused) Text("—", style = valueStyle, color = colors.onSurfaceVariant.copy(alpha = 0.5f))
                        inner()
                    }
                }
            }
        )
    }
}

/**
 * The amber family as text, lines and chart marks: amber-ink on light surfaces, full signal amber on dark ones (not
 * dark's softened primaryContainer, which is only for fills). Never used as a fill.
 */
@Composable
internal fun accentText(): Color = accentTextFor(MaterialTheme.colorScheme)

internal fun accentTextFor(scheme: ColorScheme): Color =
    if (scheme.surface.luminance() < 0.5f) SignalAmber else scheme.secondary

/** A logged set's value in the ledger: condensed and large, the same for single sets and sets with drops. */
@Composable
internal fun ledgerValueStyle(): TextStyle = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp, lineHeight = 28.sp)

/**
 * The container every logged row in the ledger shares: a card-toned rounded row whose edit action is a pencil in a
 * tonal circle at the end.
 */
@Composable
internal fun LoggedRowEditMark() {
    Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center) {
        RebuildIcon(R.drawable.ic_pencil, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp))
    }
}

/** Where a [TextButton] is drawn decides its look: dialog actions are pills, screen text buttons stay text. */
internal enum class DialogSlot { NONE, BODY, DISMISS, CONFIRM }

/**
 * The fill of a tonal control (a number field, a secondary button, a chip): one step up from what it sits on. A dialog
 * is itself surfaceContainerHigh, so inside one the control steps up to surfaceContainerHighest instead of matching
 * the dialog and vanishing until it's touched.
 */
@Composable
internal fun tonalFill(): Color =
    if (LocalDialogSlot.current != DialogSlot.NONE) MaterialTheme.colorScheme.surfaceContainerHighest
    else MaterialTheme.colorScheme.surfaceContainerHigh

/** An empty field's edge, so where to type shows before it's touched; focus and errors draw their own ring. */
@Composable
internal fun idleFieldEdge(): Color = MaterialTheme.colorScheme.outlineVariant
internal val LocalDialogSlot = androidx.compose.runtime.staticCompositionLocalOf { DialogSlot.NONE }

/**
 * The app's text button. On screens it is Material's text button. In a dialog the confirm action is a filled amber
 * pill (red when the caller colours it as an error), and dismiss and in-dialog actions are tonal pills, so every
 * modal uses the same button language as the screens.
 */
@Composable
internal fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: androidx.compose.material3.ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val destructive = colors.contentColor == scheme.error
    when (LocalDialogSlot.current) {
        // Material's default text colour is primary, an amber fill colour here; on screens it reads as accent text.
        DialogSlot.NONE -> androidx.compose.material3.TextButton(onClick = onClick, modifier = modifier,
            enabled = enabled,
            colors = if (colors.contentColor == scheme.primary) ButtonDefaults.textButtonColors(contentColor = accentText())
                else colors,
            contentPadding = contentPadding, content = content)
        DialogSlot.CONFIRM -> InkPress { androidx.compose.material3.Button(
            onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled,
            colors = if (destructive) ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError)
                else signalButtonColors(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), content = content) }
        DialogSlot.DISMISS, DialogSlot.BODY -> FilledTonalButton(
            onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled,
            colors = ButtonDefaults.filledTonalButtonColors(
                // One step above the dialog's own surfaceContainerHigh, so the pill is visible on it.
                containerColor = if (destructive) scheme.errorContainer else scheme.surfaceContainerHighest,
                contentColor = if (destructive) scheme.onErrorContainer else scheme.onSurface),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), content = content)
    }
}

/** Main actions: signal amber with ink labels, in both themes. */
@Composable
internal fun signalButtonColors() = ButtonDefaults.buttonColors(
    // Fill: softened on dark.
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
)

/**
 * The app's filled button: every main action (Log set, Next, Resume, Start…) is a signal-amber pill with an ink
 * label. Callers that pass their own colours (a destructive red, say) keep them.
 */
@Composable
internal fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: androidx.compose.material3.ButtonColors = signalButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) = InkPress { // a filled pill: an ember press would vanish into the amber
    androidx.compose.material3.Button(onClick = onClick, modifier = modifier, enabled = enabled, colors = colors,
        contentPadding = contentPadding, content = content)
}

/** A row of a grouped list: card tone, 20/4 dp corners from [groupShape], a 2 dp gap above all but the first. */
@Composable
internal fun Modifier.groupedRow(index: Int, lastIndex: Int): Modifier =
    // A shaped background, not a clip: clipping registers the shape as an accessibility node of its own, which on a
    // row scrolled to a sliver is an unnamed strip. Content sits 16 dp in, so nothing needs clipping.
    padding(top = if (index == 0) 0.dp else 2.dp)
        .background(MaterialTheme.colorScheme.surfaceContainerLowest, groupShape(index, lastIndex)).padding(horizontal = 16.dp)


/**
 * A tab inside a pill track (History's Workouts / Exercises / Body). Material's Tab paints its press over the whole
 * cell, a rectangle in its label colour. Here the press is a capsule inset like the selection pill, in the app's
 * ember, and an unselected segment gives a little under the finger, as current segmented controls do. The full cell, at least
 * 48 dp tall, still takes the tap.
 */
@Composable
internal fun PillTab(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    selectedContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val presses = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    // Only a tab you're moving to gives under the finger; the selected one keeps its pill's size.
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed && !selected) 0.96f else 1f,
        androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "tab-press")
    Box(
        modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab, interactionSource = presses,
                indication = null),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .matchParentSize()
                .padding(4.dp) // the selection pill's inset, so press and selection are the same shape
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                // On the selected (amber) pill an ember press would vanish, so it presses in the pill's label colour.
                .indication(presses, androidx.compose.material3.ripple(
                    color = if (selected) selectedContentColor else Color.Unspecified))
        )
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false,
            color = if (selected) selectedContentColor else unselectedContentColor,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.padding(horizontal = 16.dp))
    }
}
