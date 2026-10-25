package io.ichnois.spring.boot4.integrationtest

import io.ichnois.IchnoisCore
import io.ichnois.metrics.MetricsUnit
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/metric/")
class MetricController {
  @GetMapping("count")
  fun count(): String {
    IchnoisCore.setAttribute("user.type", "admin")
    IchnoisCore.setAttribute("feature.version", 2)
    IchnoisCore.metrics().count("countMetric")
    return "count metric increased"
  }

  @GetMapping("gauge/{value}")
  fun gauge(@PathVariable("value") value: Long): String {
    IchnoisCore.metrics().gauge("memory.free", value.toDouble(), MetricsUnit.Information.BYTE)
    return "gauge metric tracked"
  }

  @GetMapping("distribution/{value}")
  fun distribution(@PathVariable("value") value: Long): String {
    IchnoisCore.metrics()
      .distribution("distributionMetric", value.toDouble(), MetricsUnit.Duration.MILLISECOND)
    return "distribution metric tracked"
  }
}
