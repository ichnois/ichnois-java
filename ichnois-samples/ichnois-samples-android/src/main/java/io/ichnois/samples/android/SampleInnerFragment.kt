package io.ichnois.samples.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.ichnois.IchnoisCore
import io.ichnois.samples.android.databinding.FragmentSampleInnerBinding

class SampleInnerFragment : Fragment() {
  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View =
    FragmentSampleInnerBinding.inflate(inflater)
      .apply {
        this.sendMessage.setOnClickListener {
          IchnoisCore.captureMessage("Some message from inner Fragment Lifecycle events in breadcrumbs.")
        }
      }
      .root

  companion object {
    @JvmStatic fun newInstance() = SampleInnerFragment()
  }
}
