package io.ichnois.samples.android

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import io.ichnois.IchnoisCore
import io.ichnois.SpanStatus
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ThirdFragment : Fragment(R.layout.third_fragment) {
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    view.findViewById<View>(R.id.third_button).setOnClickListener { throw RuntimeException("Test") }
    val span = IchnoisCore.getSpan()
    val child = span?.startChild("calc")

    GitHubApi.service
      .listRepos("getichnois")
      .enqueue(
        object : Callback<List<Repo>> {
          override fun onFailure(call: Call<List<Repo>>?, t: Throwable) {
            child?.finish(SpanStatus.UNKNOWN_ERROR)
          }

          override fun onResponse(call: Call<List<Repo>>, response: Response<List<Repo>>) {
            val someInt = response.body()?.size ?: requireArguments().getInt("some_int")
            for (i in 0..someInt) {
              println(i)
            }
            child?.finish(SpanStatus.OK)
          }
        }
      )
  }
}
