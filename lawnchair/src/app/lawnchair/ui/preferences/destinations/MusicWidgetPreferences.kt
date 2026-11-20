package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

@Composable
fun MusicWidgetPreferences(
    modifier: Modifier = Modifier,
) {
    val preferenceManager2 = preferenceManager2()
    val backgroundAdapter = preferenceManager2.musicWidgetBackground.getAdapter()

    PreferenceLayout(
        label = stringResource(id = R.string.music_widget_label),
        modifier = modifier,
    ) {
        PreferenceGroup(
            heading = stringResource(id = R.string.smartspace_appearance),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            SwitchPreference(
                adapter = backgroundAdapter,
                label = stringResource(id = R.string.smartspace_background),
            )
            ExpandAndShrink(visible = backgroundAdapter.state.value) {
                SliderPreference(
                    label = stringResource(id = R.string.smartspace_background_opacity),
                    adapter = preferenceManager2.musicWidgetBackgroundOpacity.getAdapter(),
                    valueRange = 0f..1f,
                    step = 0.05f,
                    showAsPercentage = true,
                )
            }
        }
    }
}
