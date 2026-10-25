package io.ichnois.spring.jakarta.checkin;

import io.ichnois.quartz.IchnoisCoreJobListener;
import org.jetbrains.annotations.ApiStatus;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

@ApiStatus.Experimental
public final class IchnoisCoreSchedulerFactoryBeanCustomizer implements SchedulerFactoryBeanCustomizer {
  @Override
  public void customize(SchedulerFactoryBean schedulerFactoryBean) {
    schedulerFactoryBean.setGlobalJobListeners(new IchnoisCoreJobListener());
  }
}
