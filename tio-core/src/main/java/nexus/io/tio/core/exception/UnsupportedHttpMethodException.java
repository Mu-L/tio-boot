package nexus.io.tio.core.exception;

public class UnsupportedHttpMethodException extends TioDecodeException {

  private static final long serialVersionUID = 1L;

  private final String method;

  public UnsupportedHttpMethodException(String method) {
    super("Unsupported HTTP method: " + method);
    this.method = method;
  }

  public String getMethod() {
    return method;
  }

  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}
