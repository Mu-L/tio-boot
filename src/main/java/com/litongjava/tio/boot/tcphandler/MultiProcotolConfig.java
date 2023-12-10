package com.litongjava.tio.boot.tcphandler;

import com.litongjava.tio.http.common.HttpConfig;

public class MultiProcotolConfig extends HttpConfig {

  public MultiProcotolConfig(Integer bindPort, boolean useSession) {
    super(bindPort, useSession);
  }

  public MultiProcotolConfig(Integer bindPort) {
    super(bindPort, true);
  }
}
