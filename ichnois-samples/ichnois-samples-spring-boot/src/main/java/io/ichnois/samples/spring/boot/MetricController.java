package io.ichnois.samples.spring.boot;

import io.ichnois.IchnoisCore;
import io.ichnois.metrics.MetricsUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/metric/")
public class MetricController {
  private static final Logger LOGGER = LoggerFactory.getLogger(MetricController.class);

  @GetMapping("count")
  String count() {
    IchnoisCore.setAttribute("user.type", "admin");
    IchnoisCore.setAttribute("feature.version", 2);
    IchnoisCore.metrics().count("countMetric");
    return "count metric increased";
  }

  @GetMapping("gauge/{value}")
  String gauge(@PathVariable("value") Long value) {
    IchnoisCore.metrics().gauge("memory.free", value.doubleValue(), MetricsUnit.Information.BYTE);
    return "gauge metric tracked";
  }

  @GetMapping("distribution/{value}")
  String distribution(@PathVariable("value") Long value) {
    IchnoisCore.metrics()
        .distribution("distributionMetric", value.doubleValue(), MetricsUnit.Duration.MILLISECOND);
    return "distribution metric tracked";
  }
}
