package io.ichnois

import io.ichnois.clientreport.IClientReportRecorder

class IchnoisCoreOptionsManipulator {
  companion object {
    fun setClientReportRecorder(
      options: IchnoisCoreOptions,
      clientReportRecorder: IClientReportRecorder,
    ) {
      options.clientReportRecorder = clientReportRecorder
    }
  }
}
