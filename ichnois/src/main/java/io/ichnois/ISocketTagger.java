package io.ichnois;

public interface ISocketTagger {
  /** Tags the sockets traffic originating from the IchnoisCore HttpConnection thread. */
  void tagSockets();

  /** Untags the sockets traffic originating from the IchnoisCore HttpConnection thread. */
  void untagSockets();
}
