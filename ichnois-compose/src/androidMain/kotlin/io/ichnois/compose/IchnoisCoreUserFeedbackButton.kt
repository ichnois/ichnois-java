package io.ichnois.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreFeedbackOptions

@Deprecated("`IchnoisCoreUserFeedbackButton` will be removed in the next major version")
@Composable
public fun IchnoisCoreUserFeedbackButton(
  modifier: Modifier = Modifier,
  text: String = "Report a Bug",
  configurator: IchnoisCoreFeedbackOptions.OptionsConfigurator? = null,
) {
  Button(modifier = modifier, onClick = { IchnoisCore.feedback().show(configurator) }) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ichnois_user_feedback_compose_button_logo_24),
        contentDescription = null,
      )
      Spacer(Modifier.padding(horizontal = 4.dp))
      Text(text = text)
    }
  }
}
